import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Cars } from 'src/entity/cars.entity';
import { CarsController } from './cars.controller';
import { CarsService } from './cars.service';

@Module({
  imports: [TypeOrmModule.forFeature([Cars])],
  controllers: [CarsController],
  providers: [CarsService],
  exports: [CarsService]
})
export class CarsModule {}
