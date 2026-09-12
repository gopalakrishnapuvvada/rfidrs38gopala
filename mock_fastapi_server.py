#!/usr/bin/env python3
"""
Mock server to test receiving RFID tag data from the CipherLab RS38 scanner.
Endpoint: POST http://<IP>:8000/post_fixed_rfid

Usage:
  python3 mock_fastapi_server.py
"""

import json
import sys
from http.server import HTTPServer, BaseHTTPRequestHandler

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8000

class RFIDRequestHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path == "/post_fixed_rfid":
            content_length = int(self.headers.get("Content-Length", 0))
            post_data = self.rfile.read(content_length)

            try:
                data = json.loads(post_data.decode("utf-8"))
                print("\n" + "="*50)
                print("🎯 [RFID TAG RECEIVED FROM RS38]")
                print(f"  • EPC:          {data.get('epc')}")
                print(f"  • TID:          {data.get('tid')}")
                print(f"  • RSSI:         {data.get('rssi')} dBm")
                print(f"  • Device Model: {data.get('device_model')}")
                print(f"  • Timestamp:    {data.get('timestamp')}")
                print("="*50 + "\n")

                # Send 200 OK JSON response
                response = {
                    "status": "success",
                    "message": "Tag received successfully",
                    "received_epc": data.get("epc")
                }
                response_bytes = json.dumps(response).encode("utf-8")

                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(response_bytes)))
                self.end_headers()
                self.wfile.write(response_bytes)

            except Exception as e:
                print(f"❌ Error parsing JSON: {e}")
                self.send_response(400)
                self.end_headers()
        else:
            self.send_response(404)
            self.end_headers()

    def do_GET(self):
        if self.path == "/":
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"RFID Receiver Server is running on port 8000!\n")
        else:
            self.send_response(404)
            self.end_headers()

def run():
    server_address = ("0.0.0.0", PORT)
    httpd = HTTPServer(server_address, RFIDRequestHandler)
    print(f"🚀 RFID Test Server listening on http://0.0.0.0:{PORT}/post_fixed_rfid")
    print(f"   If testing with USB cable, run: adb reverse tcp:{PORT} tcp:{PORT}")
    print("   Waiting for RFID tags from CipherLab RS38...\n")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nServer stopped.")

if __name__ == "__main__":
    run()
