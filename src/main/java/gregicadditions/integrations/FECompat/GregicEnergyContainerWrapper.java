package gregicadditions.integrations.FECompat;

import javax.annotation.Nullable;

import gregicadditions.GAConfig;
import gregicadditions.GAUtility;
import gregicadditions.GAValues;
import gregtech.api.capability.IEnergyContainer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

import static gregicadditions.GAUtility.safeCastLongToInt;

public class GregicEnergyContainerWrapper implements IEnergyContainer {

    /**
     * Capability Provider of the FE TileEntity for the EU-capability.
     */
    private final ICapabilityProvider upvalue;

    /**
     * Capability holder for the FE-capability.
     */
    private final IEnergyStorage[] facesFE = new IEnergyStorage[7];

    /**
     * Internally used FE Buffer so that a very large packet of EU is not partially destroyed
     * on the conversion to FE. This is hidden from the player, but ensures that no energy
     * is ever lost on conversion, no matter the voltage tier or FE storage abilities.
     */
    private long feBuffer = 0;

    protected GregicEnergyContainerWrapper(ICapabilityProvider upvalue) {
        this.upvalue = upvalue;
    }

    /**
     * Test this EnergyContainer for sided Capabilities.
     *
     * @param side The side of the TileEntity to test for the Capability
     * @return True if side has Capability, false otherwise
     */
    protected boolean isValid(EnumFacing side) {

        if (upvalue.hasCapability(CapabilityEnergy.ENERGY, side))
            return true;

        if (side == null) {
            for (EnumFacing face2 : EnumFacing.VALUES) {
                if (upvalue.hasCapability(CapabilityEnergy.ENERGY, face2)) {
                    return true;
                }
            }
        }
        return false;
    }

    private IEnergyStorage getStorageCap() {

        IEnergyStorage container = def();

        if (container != null && container.getMaxEnergyStored() > 0)
            return container;

        for (EnumFacing face : EnumFacing.VALUES) {
            container = facesFE[face.getIndex()];

            if (container == null) {
                container = upvalue.getCapability(CapabilityEnergy.ENERGY, face);
                facesFE[face.getIndex()] = container;
            }

            if (container != null && container.getMaxEnergyStored() > 0)
                return container;
        }

        return container;
    }

    private IEnergyStorage getAcceptingCap() {

        IEnergyStorage container = def();

        if (container != null && container.canReceive())
            return container;

        for (EnumFacing face : EnumFacing.VALUES) {
            container = facesFE[face.getIndex()];

            if (container == null) {
                container = upvalue.getCapability(CapabilityEnergy.ENERGY, face);
                facesFE[face.getIndex()] = container;
            }

            if (container != null && container.canReceive())
                return container;
        }

        return container;
    }

    @Override
    public long acceptEnergyFromNetwork(EnumFacing facing, long voltage, long amperage) {

        int faceID = facing == null ? 6 : facing.getIndex();
        IEnergyStorage container = facesFE[faceID];

        if (container == null) {
            container = upvalue.getCapability(CapabilityEnergy.ENERGY, facing);
            facesFE[faceID] = container;
        }

        if (container == null)
            return 0L;

        int receive = 0;

        // Try to use the internal buffer before consuming a new packet
        if (feBuffer > 0) {

            receive = container.receiveEnergy(safeCastLongToInt(feBuffer), true);

            if (receive == 0)
                return 0;

            // Internal Buffer could provide the max RF the consumer could consume
            if (feBuffer > receive) {
                feBuffer -= receive;
                container.receiveEnergy(receive, false);
                return 0;

            // Buffer could not provide max value, save the remainder and continue processing
            } else {
                receive = safeCastLongToInt(feBuffer);
                feBuffer = 0;
            }
        }

        long maxPacket = (long) (voltage * GAConfig.EnergyConversion.RATIO);
        long maximalValue = maxPacket * amperage;

        // Try to consume our remainder buffer plus a fresh packet
        if (receive != 0) {

            int consumable = container.receiveEnergy(safeCastLongToInt(maximalValue + receive), true);

            // Machine unable to consume any power
            // Probably unnecessary in this case, but just to be safe
            if (consumable == 0)
                return 0;

            // Only able to consume our buffered amount
            if (consumable == receive) {
                container.receiveEnergy(consumable, false);
                return 0;
            }

            // Able to consume our full packet as well as our remainder buffer
            if (consumable == maximalValue + receive) {
                container.receiveEnergy(consumable, false);
                return amperage;
            }

            int newPower = consumable - receive;

            // Able to consume buffered amount plus an even amount of packets (no buffer needed)
            if (newPower % maxPacket == 0) {
                return container.receiveEnergy(consumable, false) / maxPacket;
            }

            // Able to consume buffered amount plus some amount of power with a packet remainder
            int ampsToConsume = safeCastLongToInt((newPower / maxPacket) + 1);
            feBuffer = safeCastLongToInt((maxPacket * ampsToConsume) - consumable);
            container.receiveEnergy(consumable, false);
            return ampsToConsume;

        // Else try to draw 1 full packet
        } else {

            int consumable = container.receiveEnergy(safeCastLongToInt(maximalValue), true);

            // Machine unable to consume any power
            if (consumable == 0)
                return 0;

            // Able to accept the full amount of power
            if (consumable == maximalValue) {
                container.receiveEnergy(consumable, false);
                return amperage;
            }

            // Able to consume an even amount of packets
            if (consumable % maxPacket == 0) {
                return container.receiveEnergy(consumable, false) / maxPacket;
            }

            // Able to consume power with some amount of power remainder in the packet
            int ampsToConsume = safeCastLongToInt((consumable / maxPacket) + 1);
            feBuffer = safeCastLongToInt((maxPacket * ampsToConsume) - consumable);
            container.receiveEnergy(consumable, false);
            return ampsToConsume;
        }
    }

    @Override
    public long changeEnergy(long delta) {

        IEnergyStorage container = getStorageCap();

        if (container == null || delta == 0)
            return 0;

        long energyValue = (long) (delta * GAConfig.EnergyConversion.RATIO);
        if (energyValue > Integer.MAX_VALUE)
            energyValue = Integer.MAX_VALUE;

        if (delta < 0L) {

            int extract = container.extractEnergy(safeCastLongToInt(energyValue), true);

            if (extract != GAConfig.EnergyConversion.RATIO)
                extract -= extract % GAConfig.EnergyConversion.RATIO;

            return (long) (container.extractEnergy(extract, false) / GAConfig.EnergyConversion.RATIO);

        } else {

            int receive = container.receiveEnergy((int) energyValue, true);

            if (receive != GAConfig.EnergyConversion.RATIO)
                receive -= receive % GAConfig.EnergyConversion.RATIO;

            return (long) (container.receiveEnergy(receive, false) / GAConfig.EnergyConversion.RATIO);
        }
    }

    @Nullable
    private IEnergyStorage def() {

        if (facesFE[6] == null)
            facesFE[6] = upvalue.getCapability(CapabilityEnergy.ENERGY, null);

        return facesFE[6];
    }

    @Override
    public long getEnergyCapacity() {
        IEnergyStorage cap = getStorageCap();

        if (cap == null)
            return 0L;

        return (long) (cap.getMaxEnergyStored() / GAConfig.EnergyConversion.RATIO);
    }

    @Override
    public long getEnergyStored() {
        IEnergyStorage cap = getStorageCap();

        if (cap == null)
            return 0L;

        return (long) (cap.getEnergyStored() / GAConfig.EnergyConversion.RATIO);
    }

    @Override
    public long getInputAmperage() {
        IEnergyStorage container = getAcceptingCap();

        if (container == null)
            return 0;

        long voltage = getInputVoltage();

        return voltage == 0 ? 0 : 2;
    }

    @Override
    public long getInputVoltage() {
        IEnergyStorage container = getStorageCap();

        if (container == null)
            return 0;

        long maxInput = container.receiveEnergy(Integer.MAX_VALUE, true);

        if (maxInput == 0)
            return 0;

        maxInput = (long) (maxInput / GAConfig.EnergyConversion.RATIO);
        return GAValues.V[GAUtility.getTierByVoltage(maxInput)];
    }

    @Override
    public boolean inputsEnergy(EnumFacing facing) {

        int faceID = facing == null ? 6 : facing.getIndex();
        IEnergyStorage container = facesFE[faceID];

        if (container == null) {
            container = upvalue.getCapability(CapabilityEnergy.ENERGY, facing);
            facesFE[faceID] = container;
        }

        if (container == null)
            return false;

        return container.canReceive();
    }

    /**
     * Wrapped FE-consumers should not be able to output EU.
     */
    @Override
    public boolean outputsEnergy(EnumFacing facing) {
        return false;
    }

    /**
     * Hide this TileEntity EU-capability in TOP. Allows FE-machines to
     * "silently" accept EU without showing their charge in EU in TOP.
     * Let the machine display it in FE instead, however it chooses to.
     */
    @Override
    public boolean isOneProbeHidden() {
        return true;
    }
}
