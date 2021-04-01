import { IsString } from 'class-validator';

export class AddCarDto{
    @IsString()
    readonly carPlate: string;
}