import { Body, Controller, Get, Param, Post } from '@nestjs/common';
import { CarsService } from './cars.service'
import { Cars } from 'src/entity/cars.entity'
import { AddCarDto } from 'src/dto/add-car.dto'

@Controller('cars')
export class CarsController {
    constructor(private readonly carsService: CarsService){}


    @Get()
    findAll(){
        return this.carsService.findAll();
    }

    @Get('find/:carPlate')
    findOneByPlate(@Param('carPlate') carPlate: string): Promise<Cars | null>{
        return this.carsService.findOneByPlate(carPlate)
    }

    
    @Post('add')
    addCarNumber(@Body() data: AddCarDto): Promise<Cars | undefined>{
        return this.carsService.addCar(data)
    }
}
