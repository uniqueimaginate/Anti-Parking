package com.uniqueimaginate.antiparking.retrofit

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AntiParkingService {
    @GET("cars/find/{carPlate}")
    fun getOneCar(
        @Path("carPlate") carPlate: String
    ): Call<ResultCar>

    @POST("cars/add")
    fun addCar(
        @Body car: AddCar
    ): Call<AddCar>
}