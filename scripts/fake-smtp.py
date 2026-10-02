"""받은 메일을 파일로 떨구는 최소 SMTP 서버.

메일 계정 없이 transport=smtp 경로를 검증할 때 쓴다 (3.01 auth 구현 8.5.0).

    python3 scripts/fake-smtp.py mail.eml &
    ./gradlew bootRun --args='--mijang.mail.transport=smtp \
      --spring.mail.host=localhost --spring.mail.port=1025 \
      --spring.mail.properties.mail.smtp.auth=false \
      --spring.mail.properties.mail.smtp.starttls.enable=false'

외부 패키지가 없다 — aiosmtpd 는 시스템 파이썬에 못 깔고(PEP 668),
필요한 것은 SMTP 대화의 다섯 마디뿐이다.
"""
import asyncio
import sys

OUT = sys.argv[1] if len(sys.argv) > 1 else "mail.eml"


async def handle(reader, writer):
    def send(line):
        writer.write((line + "\r\n").encode())

    send("220 fake ESMTP")
    data, in_data = [], False
    while True:
        raw = await reader.readline()
        if not raw:
            break
        line = raw.decode("utf-8", "replace").rstrip("\r\n")
        if in_data:
            if line == ".":
                in_data = False
                with open(OUT, "w", encoding="utf-8") as f:
                    f.write("\n".join(data))
                print(f"메일 저장 → {OUT}", flush=True)
                send("250 OK stored")
            else:
                # 점으로 시작하는 줄은 SMTP 가 점을 하나 겹쳐 보낸다(dot-stuffing)
                data.append(line[1:] if line.startswith("..") else line)
            continue
        verb = line.split(" ", 1)[0].upper()
        if verb in ("EHLO", "HELO"):
            send("250-fake")
            send("250 8BITMIME")
        elif verb in ("MAIL", "RCPT"):
            send("250 OK")
        elif verb == "DATA":
            in_data = True
            send("354 go")
        elif verb == "QUIT":
            send("221 bye")
            break
        else:
            send("250 OK")
    writer.close()


async def main():
    server = await asyncio.start_server(handle, "127.0.0.1", 1025)
    print("listening 127.0.0.1:1025", flush=True)
    async with server:
        await server.serve_forever()


asyncio.run(main())
