#!/bin/bash

# File path to the metadata.csv on the Raspberry Pi
CSV_FILE="metadata.csv"
# Temp directory for chunks
CHUNK_DIR="chunks"
# Android device storage path where csv will be stored
ANDROID_PATH="/sdcard/metadata.csv"
# Android package and activity name
PACKAGE_NAME="com.example.datagen"
ACTIVITY_NAME="com.example.datagen.MainActivity"

# Ensure the chunk directory exists
mkdir -p $CHUNK_DIR

# Split the CSV into chunks (skip the header from chunks after the first one)
awk -v chunk_dir="$CHUNK_DIR/chunk" '{print > (chunk_dir int((NR-1)/10))}' $CSV_FILE

# Count number of files in CHUNK_DIR
NUM_FILES=$(ls -1q $CHUNK_DIR | wc -l)

echo "[+] Filename: $CSV_FILE"
echo "[+] Chunk directory: $CHUNK_DIR"
echo "[+] Number of files: $NUM_FILES"

# Process each chunk
for ((i=0; i<$NUM_FILES; i++))
do
  CHUNK_FILE="${CHUNK_DIR}/chunk${i}"

  # Push the chunk to the Android device
  adb push $CHUNK_FILE $ANDROID_PATH
  echo "[+] Pushed $CHUNK_FILE to $ANDROID_PATH"
  
  # Start the app
  adb shell am start -n "$PACKAGE_NAME/$ACTIVITY_NAME"

  # check if the app is still running
  while [ $(adb shell pidof $PACKAGE_NAME) ]; do
    sleep 1
  done
done

# Cleanup temporary files
rm -r $CHUNK_DIR