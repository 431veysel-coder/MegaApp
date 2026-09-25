#!/bin/bash
# Run this on a machine with Java (not in PRoot)
keytool -genkey -v -keystore release.keystore -alias megaapp \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "MegaApp2026!" -keypass "MegaApp2026!" \
  -dname "CN=MegaApp, OU=SuperBrowser, O=MegaApp, L=Istanbul, S=TR, C=TR"
base64 -w0 release.keystore
