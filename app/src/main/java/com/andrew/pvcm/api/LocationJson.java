package com.andrew.pvcm.api;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public class LocationJson implements Serializable {

    private static final long serialVersionUID = -5446091721444850266L;

    @SerializedName("deviceId") String deviceId;
    @SerializedName("deviceLatitude") String deviceLatitude;
    @SerializedName("deviceLongitude") String deviceLongitude;
    @SerializedName("insertDate") String insertDate;

    public String getDeviceId() {
        return deviceId;
    }

    public String getDeviceLatitude() {
        return deviceLatitude;
    }

    public String getDeviceLongitude() {
        return deviceLongitude;
    }

    public String getInsertDate() {
        return insertDate;
    }


    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public void setDeviceLatitude(String deviceLatitude) {
        this.deviceLatitude = deviceLatitude;
    }

    public void setDeviceLongitude(String deviceLongitude) {
        this.deviceLongitude = deviceLongitude;
    }

    public void setInsertDate(String insertDate) {
        this.insertDate = insertDate;
    }

    public interface LocationJsonInterface {
        @GET("/pohang/api/getLocation.php?apiKeyVal=ehdus77")
        Call<List<LocationJson>> getLocationList(
                @Query("deviceId") String deviceId
        );
    }
}
