package com.charles445.simpledifficulty.tileentity;

import com.charles445.simpledifficulty.api.SDBlocks;
import com.charles445.simpledifficulty.block.BlockCampfire;
import com.charles445.simpledifficulty.config.ModConfig;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemStackHandler;

public class TileEntitySpit extends TileEntity implements ITickable
{
	//TODO configurable? Or is this going to be NBT hell
	//NOTE: Having only 1 slot is going to divide by zero
	public static final int SLOTS = 3;
	
	//NBT Name constants
	private static final String NBT_INT_PROGRESS = "progress";
	private static final String NBT_TAG_ITEMS = "items";
	
	//ItemStackHandler is a simple NBT container for items and slots
	public ItemHandler items;
	
	//Progress, in seconds, of the cooking
	private int progress = 0;
	
	private int timer = 0;
	
	public TileEntitySpit()
	{
		items = new ItemHandler(SLOTS);
	}
	
	//BEHAVIOR
	
	@Override
	public void update()
	{
		//Runs every tick;
		if(timer % 20 == 0)
		{
			timer = 0;
			secondUpdate();
		}
		
		timer++;
	}
	
	private void secondUpdate()
	{
		//Updates every second
		if(shouldCook())
		{
			progress++;
			if(progress >= ModConfig.server.miscellaneous.campfireSpitDelay)
			{
				cookFood();
				progress = 0;
			}
		}
	}
	
	private void cookFood()
	{
		for(int i=0; i < items.getSlots(); i++)
		{
			ItemStack stack = items.getStackInSlot(i);
			
			if(isCookable(stack))
			{
				items.setStackInSlot(i, FurnaceRecipes.instance().getSmeltingResult(stack).copy());
			}
		}
	}
	
	private void playWorldSound(World world, BlockPos pos)
	{
		//TODO play sound
	}
	
	public void handleRightClick(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ)
	{
		ItemStack heldItemStack = player.getHeldItem(hand);
		
		boolean rawWithdraw = heldItemStack.isEmpty();
		
		//Deposit
		if(isCookable(heldItemStack))
		{
			for(int i=0; i < items.getSlots(); i++)
			{
				if(items.getStackInSlot(i).isEmpty())
				{
					//Insert and break
					items.insertItem(i, new ItemStack(heldItemStack.getItem(), 1, heldItemStack.getItemDamage()), false);
					heldItemStack.shrink(1);
					
					//Reset Progress
					//TODO individual slot progress? lol
					progress = 0;
					
					playWorldSound(world, pos);
					
					break;
				}
			}
		}
		
		//And withdraw
		boolean found = false;
		
		//First look for cooked
		for(int i=0; i < items.getSlots(); i++)
		{
			if(isCooked(items.getStackInSlot(i)))
			{
				withdrawFromSlot(player, hand, i);
				playWorldSound(world, pos);
				found = true;
				break;
			}
		}
		
		if(!found && rawWithdraw && player.isSneaking())
		{
			//Take uncooked instead
			for(int i=0; i < items.getSlots(); i++)
			{
				if(!items.getStackInSlot(i).isEmpty())
				{
					withdrawFromSlot(player, hand, i);
					playWorldSound(world, pos);
					break;
				}
			}
		}
	}
	
	private void withdrawFromSlot(EntityPlayer player, EnumHand hand, int slot)
	{
		ItemStack stack = items.extractItem(slot, 1, false);
		
		if(player.getHeldItem(hand).isEmpty())
		{
			player.setHeldItem(hand, stack);
		}
		else if(!player.inventory.addItemStackToInventory(stack))
		{
			player.dropItem(stack, false);
		}
		else
		{
			if(player instanceof EntityPlayerMP)
				((EntityPlayerMP)player).sendContainerToPlayer(player.inventoryContainer);
		}
			
	}
	
	private boolean isCookable(ItemStack stack)
	{
		if(stack.isEmpty())
			return false;
		ItemStack result = FurnaceRecipes.instance().getSmeltingResult(stack);
		
		if(!result.isEmpty() && result.getItem() instanceof ItemFood)
		{
			return true;
		}
		
		return false;
	}
	
	private boolean isCooked(ItemStack stack)
	{
		return !stack.isEmpty() && !isCookable(stack);
	}
	
	private boolean shouldCook()
	{
		boolean hasItem = false;
		
		for(int i=0; i<items.getSlots();i++)
		{
			if(isCookable(items.getStackInSlot(i)))
			{
				hasItem = true;
				break;
			}
		}
		
		if(!hasItem)
			return false;
	
		IBlockState state = world.getBlockState(pos.down());
		Block block = state.getBlock();
		
		//Evaluate the kind of fire below
		if(block == SDBlocks.campfire)
		{
			return state.getValue(BlockCampfire.BURNING);
		}
		
		return false;
	}
	
	public void dumpItems(World world, BlockPos pos)
	{
		//Dump all items at the specified position and empty inventory
		//This is basically InventoryHelper's dropInventoryItems
		for (int i = 0; i < items.getSlots(); i++)
		{
			ItemStack itemstack = items.getStackInSlot(i);

			if (!itemstack.isEmpty())
			{
				//And now ACTUALLY call InventoryHelper...
				//Forge is really bizarre sometimes...
				InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), itemstack);
			}
			
			//Oh, and clear out the inventory
			//Just so we don't get a double dump
			
			items.setStackInSlot(i, ItemStack.EMPTY);
		}
	}
	
	//NBT HANDLING
	public void readFromNBT(NBTTagCompound compound)
	{
		//Internal NBT first
		super.readFromNBT(compound);
		
		//Progress as int
		progress = compound.getInteger(NBT_INT_PROGRESS);
		
		//Items as compound
		items.deserializeNBT(compound.getCompoundTag(NBT_TAG_ITEMS));
		
		//Now read the other data necessary
	}

	public NBTTagCompound writeToNBT(NBTTagCompound compound)
	{
		//Internal NBT first
		compound = super.writeToNBT(compound);
		
		//Progress as int
		compound.setInteger(NBT_INT_PROGRESS, progress);
		
		//Items as compound (what's up with there being no setCompoundTag anyway?)
		compound.setTag(NBT_TAG_ITEMS, items.serializeNBT());
		
		return compound;
	}
	
	//NETWORKING
	
	@Override
	public NBTTagCompound getUpdateTag()
	{
		return writeToNBT(new NBTTagCompound());
		
	}
	
	//Update packet... this is how these work, right?
	//Anyway, this is the server creating the mssage
	@Override
	public SPacketUpdateTileEntity getUpdatePacket()
	{
		return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
	}
	
	//And this is the client receiving said package, right?
	@Override
	public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt)
	{
		super.onDataPacket(net, pkt);
		readFromNBT(pkt.getNbtCompound());
		
		updateClients();
	}
	
	public void updateClients()
	{
		IBlockState state = world.getBlockState(pos);
		
		//No block update, but send information to clients
		world.notifyBlockUpdate(pos, state, state, 2);
	}
	
	//ITEM HANDLING CLASS
	
	public class ItemHandler extends ItemStackHandler
	{
		//This item handler has a max stack size of 1 (aka 1 per slot)
		//This item handler also only takes items that, when heated up, turn into food
		
		public ItemHandler(int slots)
		{
			super(slots);
		}
		
		@Override
		protected void onContentsChanged(int slot)
		{
			super.onContentsChanged(slot);
			TileEntitySpit.this.markDirty();
			TileEntitySpit.this.updateClients();
	    }
		
		@Override
	    public int getSlotLimit(int slot)
	    {
	        return 1;
	    }
	}

	
	
}
