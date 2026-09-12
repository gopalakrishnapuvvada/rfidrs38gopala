#!/usr/bin/env python3
"""
Mock server to test receiving both RFID and QR Code scans from CipherLab RS38.
Endpoint: POST http://<IP>:8000/post_fixed_rfid

Usage:
  python3 mock_fastapi_server.py [port]
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
                payload = json.loads(post_data.decode("utf-8"))
                scan_type = payload.get("scan_type", "UNKNOWN")

                print("\n" + "="*55)
                if scan_type == "QR":
                    print("📱 [QR CODE / BARCODE SCANNED FROM RS38]")
                    print(f"  • Content:      {payload.get('data')}")
                    print(f"  • Device Model: {payload.get('device_model')}")
                    print(f"  • Timestamp:    {payload.get('timestamp')}")
                elif scan_type == "RFID":
                    print("🏷️  [RFID TAG SCANNED FROM RS38]")
                    print(f"  • EPC:          {payload.get('epc')}")
                    print(f"  • TID:          {payload.get('tid')}")
                    print(f"  • RSSI:         {payload.get('rssi')} dBm")
                    print(f"  • Device Model: {payload.get('device_model')}")
                    print(f"  • Timestamp:    {payload.get('timestamp')}")
                else:
                    print(f"📦 [{scan_type} DATA RECEIVED FROM RS38]")
                    print(f"  • Payload: {payload}")
                print("="*55 + "\n")

                # Send 200 OK JSON response
                response = {
                    "status": "success",
                    "scan_type": scan_type,
                    "message": f"{scan_type} scan processed successfully",
                    "received": payload.get("data") if scan_type == "QR" else payload.get("epc")
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
            self.wfile.write(b"CipherLab RS38 Dual Scanner Receiver running on port " + str(PORT).encode() + b"!\n")
        else:
            self.send_response(404)
            self.end_headers()

def run():
    server_address = ("0.0.0.0", PORT)
    httpd = HTTPServer(server_address, RFIDRequestHandler)
    print(f"🚀 Dual Scanner Test Server listening on http://0.0.0.0:{PORT}/post_fixed_rfid")
    print(f"   Accepts both UHF RFID tags and 2D QR codes.")
    print("   Waiting for scans from CipherLab RS38...\n")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nServer stopped.")

if __name__ == "__main__":
    run()
