package com.andrew.pvcm.api;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public class InsertLocation implements Serializable {

    private static final long serialVersionUID = 3364398294752417632L;

    @SerializedName("resultCode") String resultCode;
    @SerializedName("result") String result;

    public String getResultCode() { return resultCode; }
    public String getResult() { return result; }

    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    public void setResult(String result) { this.result = result; }

    public interface InsertLocatonInterface {
        @GET("/pohang/api/insertLocation.php?apiKeyVal=ehdus77")
        Call<InsertLocation> insertLocation(
                @Query("deviceId") String deviceId
                , @Query("deviceLatitude") String deviceLatitude
                , @Query("deviceLongitude") String deviceLongitude
        );
    }
}
