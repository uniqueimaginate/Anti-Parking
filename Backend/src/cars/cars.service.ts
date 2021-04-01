import { BadRequestException, Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Cars } from 'src/entity/cars.entity'
import { AddCarDto } from 'src/dto/add-car.dto'

@Injectable()
export class CarsService {
    constructor(
        @InjectRepository(Cars) private carsRepository: Repository<Cars>,
    ){}

    async findAll(): Promise<Cars []>{
        return await this.carsRepository.find()
    }

    async findOneByPlate(carPlate: string): Promise<Cars | null>{
        try{
            console.log(carPlate)
            var car =  await this.carsRepository.findOne({where: {carPlate: carPlate}})
            if(!car){
                throw new BadRequestException()
            }
            return car
        } catch(e){
            throw new BadRequestException()
        }
    }

    async addCar(data: AddCarDto): Promise<Cars | undefined>{
        try{
            const car = this.carsRepository.create(data)
            return await this.carsRepository.save(car)
        } catch(e){
            throw new BadRequestException()
        }
    }
}
