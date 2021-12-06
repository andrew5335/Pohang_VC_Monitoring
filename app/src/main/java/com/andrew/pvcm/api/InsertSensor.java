package com.andrew.pvcm.api;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public class InsertSensor implements Serializable {

    private static final long serialVersionUID = 4630030078516738634L;

    @SerializedName("resultCode") String resultCode;
    @SerializedName("result") String result;

    public String getResultCode() { return resultCode; }
    public String getResult() { return result; }

    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    public void setResult(String result) { this.result = result; }

    public interface InsertSensorInterface {
        @GET("/pohang/api/insertSensor.php?apiKeyVal=ehdus77")
        Call<InsertSensor> insertSensor (
                @Query("deviceId") String deviceId
                , @Query("deviceAcx") String deviceAcx
                , @Query("deviceAcy") String deviceAcy
                , @Query("deviceAcz") String deviceAcz
                , @Query("deviceGyx") String deviceGyx
                , @Query("deviceGyy") String deviceGyy
                , @Query("deviceGyz") String deviceGyz
        );
    }
}
