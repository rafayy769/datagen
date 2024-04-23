from fastapi import FastAPI, HTTPException, Request, Response
from fastapi.responses import FileResponse
import os
from pydantic import BaseModel

app = FastAPI()

class SensorDataUpload(BaseModel):
    location: str
    accelerometerData: list[str]
    gyroscopeData: list[str]

@app.get("/{filename:path}")
async def get_audio(filename: str):
    if not os.path.exists(filename):
        raise HTTPException(status_code=404, detail="File not found")
    return FileResponse(filename)

@app.post("/uploadSensorData")
async def upload_sensor_data(data: SensorDataUpload):

    
    base_path = "/".join(data.location.strip().split("/")[:-1])

    if not os.path.exists(base_path):
        os.makedirs(base_path)
    
    print("Base path is : ", base_path)

    accelerometer_path = os.path.join(base_path, "accelerometer.acc")
    gyroscope_path = os.path.join(base_path, "gyroscope.gyro")

    with open(accelerometer_path, "w") as f:
        print("created acc")
        for line in data.accelerometerData:
            f.write(f"{line}\n")

    with open(gyroscope_path, "w") as f:
        print("created gyro")
        for line in data.gyroscopeData:
            f.write(f"{line}\n")

    return {"message": "Data uploaded successfully"}