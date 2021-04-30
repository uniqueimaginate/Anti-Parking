import { Body, Controller, Delete, Get, Param, Post } from '@nestjs/common';
import { CarsService } from './cars.service'
import { Cars } from 'src/entity/cars.entity'
import { AddCarDto } from 'src/dto/add-car.dto'
import { DeleteResult } from 'typeorm';

@Controller('cars')
export class CarsController {
    constructor(private readonly carsService: CarsService){}


    @Get()
    async findAll(){
        return await this.carsService.findAll();
    }

    @Get('find/:carPlate')
    async findOneByPlate(@Param('carPlate') carPlate: string): Promise<Cars | null>{
        return await this.carsService.findOneByPlate(carPlate)
    }

    
    @Post('add')
    async addCarNumber(@Body() data: AddCarDto): Promise<Cars | undefined>{
        return await this.carsService.addCar(data)
    }

    @Delete('delete/:carPlate')
    async deleteCarNumber(@Param('carPlate') carPlate: string): Promise<DeleteResult | null>{
        return await this.carsService.deleteOneByPlate(carPlate)
    }
}
