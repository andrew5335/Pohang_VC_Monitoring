package com.andrew.pvcm.api.response;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class ApiResponse implements Serializable {

    private static final long serialVersionUID = 6425136152012876283L;

    @SerializedName("resultCode") String resultCode;
    @SerializedName("result") String result;

    public String getResultCode() { return resultCode; }
    public String getResult() { return result; }
}
