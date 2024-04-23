# Datagen

Datagen goes through the provided `metadata.csv` file and plays the audio files. In the meanwhile, the appliation collects IMU sensor traces. The application requests the backend for the files, given that the size of dataset can be too large to fit on a phone's storage.

### Usage:
- Install the pre-requisites for the backend, present in the `backend` directory, by running `pip install -r requirements.txt` in a virtual environemnt (to avoid conflicts).
- Deploy the backend server either locally, or remotely. Make sure the backend has access to the audio files.
- The app is simple to use. Once started, the app initiates the process, and keeps going until all the entries in the `metadata.csv` have been played.
- There is also a `runner.sh` script in the `backend` directory, that creates chunks of the metadata file, and then sends the chunks to the device as `metadata.csv`. This way, the device can be triggered by the server repeatedly, and the device stays unlocked.
- The `backend` is a simple file hosting web server written in Python FastAPI.
