package com.uniqueimaginate.antiparking

data class ResultCar(
        var uid: Int,
        var createdAt: String,
        var carPlate: String
)

data class AddCar(
        var carPlate: String
)