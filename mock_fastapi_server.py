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
                device_id = payload.get("deviceId", payload.get("device_model", "unknown-device"))

                print("\n" + "="*58)
                if "rfidUniqueId" in payload:
                    print(f"🏷️  [RFID UNIQUE ID RECEIVED FROM {device_id}]")
                    print(f"  • RFID Tag ID: {payload.get('rfidUniqueId')}")
                    print(f"  • Device ID:   {device_id}")
                elif "materialCode" in payload:
                    print(f"📦 [MATERIAL CODE SCANNED FROM {device_id}]")
                    print(f"  • Material Code: {payload.get('materialCode')}")
                    print(f"  • Device ID:     {device_id}")
                elif "workOrderNo" in payload:
                    print(f"📋 [WORK ORDER NO SCANNED FROM {device_id}]")
                    print(f"  • Work Order No: {payload.get('workOrderNo')}")
                    print(f"  • Device ID:     {device_id}")
                elif payload.get("scan_type") == "QR" or "data" in payload:
                    print(f"📱 [QR CODE / BARCODE SCANNED FROM {device_id}]")
                    print(f"  • Content:       {payload.get('data')}")
                    print(f"  • Device ID:     {device_id}")
                elif payload.get("scan_type") == "RFID" or "epc" in payload:
                    print(f"🏷️  [RFID TAG SCANNED FROM {device_id}]")
                    print(f"  • EPC:           {payload.get('epc')}")
                    print(f"  • Device ID:     {device_id}")
                else:
                    print(f"📩 [DATA RECEIVED FROM {device_id}]")
                    print(f"  • Payload: {payload}")
                print("="*58 + "\n")

                # Send 200 OK JSON response
                response = {
                    "status": "success",
                    "message": "Scan processed successfully",
                    "data": payload
                }
                response_bytes = json.dumps(response, indent=2).encode("utf-8")

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
    HTTPServer.allow_reuse_address = True
    httpd = HTTPServer(server_address, RFIDRequestHandler)
    print(f"🚀 Dual Scanner Test Server listening on http://0.0.0.0:{PORT}/post_fixed_rfid")
    print(f"   Accepts RFID, Material Codes, and Work Order numbers.")
    print("   Waiting for scans from CipherLab RS38...\n")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nServer stopped.")

if __name__ == "__main__":
    run()
