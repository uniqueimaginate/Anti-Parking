import { IsString } from 'class-validator'
import {Column, Entity, PrimaryGeneratedColumn} from 'typeorm'

@Entity()
export class Cars{
    @PrimaryGeneratedColumn()
    uid: number;

    @Column({ type: 'timestamp', default: () => 'CURRENT_TIMESTAMP' })
    createdAt: Date;

    @Column({unique: true})
    @IsString()
    carPlate: string;
}