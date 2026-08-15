#!/usr/bin/env python3
"""Scan BLE advertisements and detect Bittr mesh adverts.

Bittr advertises:
  - manufacturer data with company ID 0xFFFF (the 22-byte AdvertPacket):
      [1B version][1B roomId][4B deviceId][16B merkleRoot]
  - scan response with company ID 0xFFFE (NicknamePacket):
      [1B version][4B deviceId][1B nickLen][nick bytes]

Room id is 0x0A. A "non-bittr advert" is any 0xFFFF manufacturer payload
that isn't 22 bytes (other apps/OS services also use company id 0xFFFF).

Usage: python3 scan_adverts.py [seconds]
"""
import asyncio
import sys

from bleak import BleakScanner

ADVERT_COMPANY_ID = 0xFFFF
NICKNAME_COMPANY_ID = 0xFFFE
ROOM_ID = 0x0A
PACKET_SIZE = 22


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


async def main(seconds: float):
    print(f"Scanning for {seconds}s ...")
    scanner = BleakScanner(detection_callback=detection_callback)
    await scanner.start()
    await asyncio.sleep(seconds)
    await scanner.stop()
    print("Done.")


if __name__ == "__main__":
    dur = float(sys.argv[1]) if len(sys.argv) > 1 else 15.0
    asyncio.run(main(dur))
