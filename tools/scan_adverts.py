#!/usr/bin/env python3
"""Scan BLE advertisements and detect Bittr mesh adverts.

Bittr advertises:
  - manufacturer data with company ID 0xFFFF (the 22-byte AdvertPacket):
      [1B version][1B roomId][4B deviceId][16B merkleRoot]
  - scan response with company ID 0xFFFE (NicknamePacket):
      [1B version][4B deviceId][1B nickLen][nick bytes]

Room id is 0x0A. A "non-bittr advert" is any 0xFFFF manufacturer payload
that isn't 22 bytes (other apps/OS services also use company id 0xFFFF).

In addition to passive scanning, this script can connect over GATT to a
Bittr device and inspect its GATT table to verify the GATT server side
actually works. See --connect and --gatt.

Usage:
  python3 scan_adverts.py [seconds]
  python3 scan_adverts.py --connect <address>
  python3 scan_adverts.py --gatt [seconds]
"""
import argparse
import asyncio
import sys

from bleak import BleakClient, BleakScanner
from bleak.exc import BleakError

ADVERT_COMPANY_ID = 0xFFFF
NICKNAME_COMPANY_ID = 0xFFFE
ROOM_ID = 0x0A
PACKET_SIZE = 22

SERVICE_UUID = "7b3e4c10-0000-1000-8000-00805f9b34fb"
CHAR_MERKLE_QUERY_UUID = "7b3e4c10-0001-1000-8000-00805f9b34fb"
CHAR_EVENT_FETCH_UUID = "7b3e4c10-0002-1000-8000-00805f9b34fb"
CHAR_IDENTITY_UUID = "7b3e4c10-0003-1000-8000-00805f9b34fb"


def fmt_device_id(device_id: int) -> str:
    return f"{device_id & 0xFFFFFFFF:08x}"


def decode_advert(data: bytes):
    if len(data) != PACKET_SIZE:
        return None
    version = data[0]
    room_id = data[1]
    device_id = int.from_bytes(data[2:6], "big")
    root = data[6:22].hex()
    return {"version": version, "roomId": room_id, "deviceId": device_id, "root": root}


def decode_nickname(data: bytes):
    if len(data) < 6:
        return None
    version = data[0]
    device_id = int.from_bytes(data[1:5], "big")
    nick_len = data[5]
    if 6 + nick_len > len(data):
        return None
    nickname = data[6:6 + nick_len].decode("utf-8", errors="replace")
    return {"version": version, "deviceId": device_id, "nickname": nickname}


def detection_callback(device, advertisement_data):
    msd = advertisement_data.manufacturer_data
    address = device.address
    name = device.name or "?"

    packet = msd.get(ADVERT_COMPANY_ID)
    nick_payload = msd.get(NICKNAME_COMPANY_ID)

    adv = decode_advert(packet) if packet is not None else None
    nick = decode_nickname(nick_payload) if nick_payload is not None else None
    rssi = advertisement_data.rssi

    if packet is None and nick_payload is None:
        return

    if adv is not None:
        if adv["roomId"] != ROOM_ID:
            print(f"[{address}] 0xFFFF roomId={adv['roomId']:#04x} (not bittr)  rssi={rssi}")
            return
        nick_str = f" nickname={nick['nickname']!r}" if nick else " nickname=(none)"
        print(
            f"[{address}] BITT R  dev={fmt_device_id(adv['deviceId'])} "
            f"root={adv['root']}{nick_str}  rssi={rssi}  name={name}"
        )
    else:
        if packet is not None:
            print(
                f"[{address}] non-bittr advert (len={len(packet)}) "
                f"company=0xFFFF  rssi={rssi}  name={name}"
            )
        if nick is not None:
            print(
                f"[{address}] nickname-only  dev={fmt_device_id(nick['deviceId'])} "
                f"nickname={nick['nickname']!r}  rssi={rssi}  name={name}"
            )


async def scan(seconds: float):
    """Run a passive scan for `seconds` seconds, printing detected adverts."""
    print(f"Scanning for {seconds}s ...")
    scanner = BleakScanner(detection_callback=detection_callback)
    await scanner.start()
    await asyncio.sleep(seconds)
    await scanner.stop()
    print("Done.")


async def find_first_bittr(seconds: float):
    """Scan until the first Bittr device is seen (or `seconds` elapse).

    Returns the device address of the first detected Bittr device, or None.
    """
    found = []

    def cb(device, advertisement_data):
        msd = advertisement_data.manufacturer_data
        packet = msd.get(ADVERT_COMPANY_ID)
        if packet is None:
            return
        adv = decode_advert(packet)
        if adv is not None and adv["roomId"] == ROOM_ID and not found:
            found.append(device.address)

    print(f"Scanning for a Bittr device ({seconds}s) ...")
    scanner = BleakScanner(detection_callback=cb)
    await scanner.start()
    await asyncio.sleep(seconds)
    await scanner.stop()

    if not found:
        print("No Bittr device detected.")
        return None
    address = found[0]
    print(f"Detected Bittr device: {address}")
    return address


async def list_gatt(client: BleakClient):
    """Log every GATT service and characteristic exposed by the device."""
    services = await client.get_services()
    print(f"\nGATT services on {client.address}:")
    for service in services:
        print(f"  service {service.uuid}")
        for char in service.characteristics:
            props = ",".join(char.properties) if char.properties else "(none)"
            print(f"    char {char.uuid}  props=[{props}]")
    print()


async def read_identity(client: BleakClient):
    """Read CHAR_IDENTITY (read-only) and print the UTF-8 username."""
    try:
        data = await client.read_gatt_char(CHAR_IDENTITY_UUID)
    except BleakError as exc:
        print(f"  identity characteristic not found / unreadable: {exc}")
        return
    print(f"  CHAR_IDENTITY raw bytes ({len(data)}): {data.hex()}")
    try:
        username = data.decode("utf-8")
    except UnicodeDecodeError:
        print(f"  username (utf-8 decode failed): {data!r}")
        return
    print(f"  username: {username!r}")


async def connect_and_inspect(address: str):
    """Connect to `address` over GATT, dump services, and read identity."""
    print(f"Connecting to {address} ...")
    client = BleakClient(address, timeout=15.0)
    try:
        await client.connect()
    except BleakError as exc:
        print(f"  connect failed: {exc}")
        print("  (device may be out of range, not connectable, or the address is wrong)")
        return
    except asyncio.TimeoutError:
        print("  connect timed out.")
        return

    print(f"Connected: {client.is_connected}")

    try:
        await list_gatt(client)
        await read_identity(client)
    except BleakError as exc:
        print(f"  GATT error: {exc}")
    finally:
        await client.disconnect()
        print("Disconnected.")


async def main(args):
    if args.connect:
        await connect_and_inspect(args.connect)
        return

    if args.gatt:
        address = await find_first_bittr(args.seconds)
        if address is None:
            return
        await connect_and_inspect(address)
        return

    await scan(args.seconds)


def parse_args(argv):
    parser = argparse.ArgumentParser(
        description="Scan Bittr BLE adverts and/or inspect a device's GATT table."
    )
    parser.add_argument(
        "seconds",
        nargs="?",
        type=float,
        default=15.0,
        help="scan duration in seconds (default 15)",
    )
    parser.add_argument(
        "--connect",
        metavar="ADDRESS",
        help="connect to a specific BLE address and inspect its GATT table",
    )
    parser.add_argument(
        "--gatt",
        action="store_true",
        help="scan for a Bittr device then connect to the first one found",
    )
    return parser.parse_args(argv)


if __name__ == "__main__":
    asyncio.run(main(parse_args(sys.argv[1:])))
