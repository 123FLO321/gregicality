package gregicadditions.machines.multi.multiblockpart;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.network.node.INetworkNode;
import com.raoulvdberge.refinedstorage.api.util.Action;
import gregtech.api.gui.ModularUI;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.common.metatileentities.electric.multiblockpart.MetaTileEntityMultiblockPart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class MetaTileEntityDigitalItemBus extends MetaTileEntityMultiblockPart implements IMultiblockAbilityPart<IItemHandlerModifiable>, INetworkNode {

    protected INetwork network;
    public static final String ID = "digital_item_bus";

    public MetaTileEntityDigitalItemBus(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, 1);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(MetaTileEntityHolder metaTileEntityHolder) {
        return new MetaTileEntityDigitalItemBus(metaTileEntityId);
    }

    @Override
    protected ModularUI createUI(EntityPlayer entityPlayer) {
        return null;
    }

    @Override
    public MultiblockAbility<IItemHandlerModifiable> getAbility() {
        return MultiblockAbility.EXPORT_ITEMS;
    }

    @Override
    public void registerAbilities(List<IItemHandlerModifiable> list) {
        list.add(this.exportItems);
    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        return new ItemStackHandler(0);
    }

    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        return new ItemStackHandler(16) {
            @Override
            @Nonnull
            public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
                if (stack.isEmpty()) {
                    return ItemStack.EMPTY;
                }

                //Do we need this?
                validateSlotIndex(slot);

                ItemStack result;
                if (simulate) {
                    result = network.insertItem(stack, stack.getCount(), Action.SIMULATE);
                } else {
                    result = network.insertItemTracked(stack, stack.getCount());
                }
                if (result == null) {
                    return ItemHandlerHelper.copyStackWithSize(stack, 0);
                } else {
                    return ItemHandlerHelper.copyStackWithSize(stack, stack.getCount() - result.getCount());
                }
            }
        };
    }

    @Override
    public int getEnergyUsage() {
        return 0;
    }

    @Nonnull
    @Override
    public ItemStack getItemStack() {
        return this.getStackForm();
    }

    @Override
    public void onConnected(INetwork network) {
        this.network = network;
    }

    @Override
    public void onDisconnected(INetwork network) {
        this.network = null;
    }

    @Override
    public boolean canUpdate() {
        if (network != null) {
            return network.canRun();
        }
        return false;
    }

    @Nullable
    @Override
    public INetwork getNetwork() {
        return network;
    }

    @Override
    public NBTTagCompound write(NBTTagCompound tag) {
        tag.setString("Version", RS.VERSION);
        tag.setInteger("Direction", frontFacing.ordinal());
        return tag;
    }

    @Override
    public String getId() {
        return ID;
    }
}
