package core.cartridge.mappers;

import utils.IntegerWrapper;


public class Mapper002 extends Mapper {

    private int selected_PRG_bank_low = 0x00;
    private int selected_PRG_bank_high = 0x00;

    /**
     * @param nPRGBanks number of Program ROM Banks
     * @param nCHRBanks number of Character ROM Banks
     */
    public Mapper002(int nPRGBanks, int nCHRBanks) {
        super(nPRGBanks, nCHRBanks);
        reset();
    }

    /**
     * @param addr   the CPU Address to map
     * @param mapped the Wrapper where to store the Mapped Address
     * @param data   if there is data to be read, it will be written there
     * @return Whether or not the Address was mapped
     */
    @Override
    public boolean cpuMapRead(int addr, IntegerWrapper mapped, IntegerWrapper data) {
        if (addr >= 0x8000 && addr <= 0xBFFF) {
            mapped.value = (selected_PRG_bank_low * 0x4000) + (addr & 0x3FFF);
            return true;
        }
        if (addr >= 0xC000) {
            mapped.value = (selected_PRG_bank_high * 0x4000) + (addr & 0x3FFF);
            return true;
        }
        return false;
    }

    /**
     * @param addr   the CPU Address to map
     * @param mapped the Wrapper where to store the Mapped Address
     * @param data   the data to write
     * @return Whether or not the Address was mapped
     */
    @Override
    public boolean cpuMapWrite(int addr, IntegerWrapper mapped, int data) {
        if (addr >= 0x8000) {
            selected_PRG_bank_low = data & 0x0F;
        }
        return false;
    }

    /**
     * @param addr   the PPU Address to map
     * @param mapped the Wrapper where to store the Mapped Address
     * @param data   if there is data to be read, it will be written there
     * @return Whether or not the Address was mapped
     */
    @Override
    public boolean ppuMapRead(int addr, IntegerWrapper mapped, IntegerWrapper data) {
        if (addr <= 0x1FFF) {
            mapped.value = addr;
            return true;
        }
        return false;
    }

    /**
     * @param addr   the PPU Address to map
     * @param mapped the Wrapper where to store the Mapped Address
     * @param data   the data to write
     * @return Whether or not the Address was mapped
     */
    @Override
    public boolean ppuMapWrite(int addr, IntegerWrapper mapped, int data) {
        if (addr <= 0x1FFF) {
            if (nb_CHR_banks == 0) {
                mapped.value = addr;
                return true;
            }
        }
        return false;
    }

    /**
     * Reset the lower PRG bank to the first and higher PRG bank to the last one
     */
    @Override
    public void reset() {
        selected_PRG_bank_low = 0;
        selected_PRG_bank_high = nb_PRG_banks - 1;
    }
}
