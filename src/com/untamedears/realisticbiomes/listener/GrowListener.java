package com.untamedears.realisticbiomes.listener;

import java.util.HashMap;
import java.util.logging.Logger;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.ItemStack;

import com.untamedears.realisticbiomes.GrowthConfig;
import com.untamedears.realisticbiomes.RealisticBiomes;
import com.untamedears.realisticbiomes.persist.Coords;
import com.untamedears.realisticbiomes.persist.Plant;
import com.untamedears.realisticbiomes.persist.WorldID;

/**
 * Event listener for all plant growth related events. Whenever a crop, plant block, or sapling attempts to grow, its type
 * is checked against the biomes in which it is permitted to grow. If the biome is not permitted, the event is canceled and
 * the plant does not grow. Additionally, all instances of bonemeal being used as fertilizer are canceled.
 * @author WildWeazel
 *
 */
public class GrowListener implements Listener {
	public static Logger LOG = Logger.getLogger("RealisticBiomes");
	
	private static HashMap<TreeType, TreeType> treeTypeMap;
	
	static {
		treeTypeMap = new HashMap<TreeType, TreeType>();
		
		treeTypeMap.put(TreeType.BIG_TREE, TreeType.TREE);
		treeTypeMap.put(TreeType.BIRCH, TreeType.BIRCH);
		treeTypeMap.put(TreeType.BROWN_MUSHROOM, TreeType.BROWN_MUSHROOM);
		treeTypeMap.put(TreeType.JUNGLE, TreeType.JUNGLE);
		treeTypeMap.put(TreeType.JUNGLE_BUSH, TreeType.JUNGLE);
		treeTypeMap.put(TreeType.RED_MUSHROOM, TreeType.RED_MUSHROOM);
		treeTypeMap.put(TreeType.REDWOOD, TreeType.REDWOOD);
		treeTypeMap.put(TreeType.SMALL_JUNGLE, TreeType.JUNGLE);
		treeTypeMap.put(TreeType.SWAMP, TreeType.TREE);
		treeTypeMap.put(TreeType.TALL_REDWOOD, TreeType.REDWOOD);
		treeTypeMap.put(TreeType.TREE, TreeType.TREE);
	}
	
	private HashMap<Object, GrowthConfig> growthMap;
	RealisticBiomes plugin;
	
	public GrowListener(RealisticBiomes plugin, HashMap<Object, GrowthConfig> growthMap) {
		super();
		
		this.plugin = plugin;
		this.growthMap = growthMap;
	}

	/**
	 *  Event handler for {@link BlockGrowEvent}. Checks plant growth for proper conditions
	 * @param event The {@link BlockGrowEvent} being handled
	 */
	@EventHandler(ignoreCancelled = true)
	public void onBlockGrow(BlockGrowEvent event) {
		Material m = event.getNewState().getType();
		Block b = event.getBlock();
		GrowthConfig growthConfig = growthMap.get(m);
		
		if (plugin.persistConfig.enabled && growthConfig != null && growthConfig.isPersistent()) {
			plugin.growAndPersistBlock(b, growthConfig, true);
			
			event.setCancelled(true);
		}
		else {
			event.setCancelled(!willGrow(m, b));
		}
	}

	/**
	 * Event handler for {@link StructureGrowEvent}. Checks tree growth for proper conditions
	 * @param event The {@link StructureGrowEvent} being handled
	 */
	@EventHandler(priority = EventPriority.HIGH)
	public void onStructureGrow(StructureGrowEvent event) {
		// disable bonemeal
		if (event.isFromBonemeal()) {
			event.setCancelled(true);
			return;
		}
		
		TreeType t = event.getSpecies();
		
		// map the tree type down to a smaller set of tree types
		// representing the types of saplings
		if (treeTypeMap.containsKey(t))
			t = treeTypeMap.get(t);
		
		Block b = event.getLocation().getBlock();
		event.setCancelled(!willGrow(t, b));
	}

	/**
	 * Event handler for {@link PlayerInteractEvent}. Cancels all uses of Bonemeal as an item on crops registered in the config.
	 * @param event The {@link PlayerInteractEvent} being handled
	 */
	@EventHandler(ignoreCancelled = true)

	public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = event.getPlayer().getItemInHand();
            // Ink Sack with data 15  == Bone Meal
            if (item.getTypeId() == 351 && item.getData().getData() == 15) {
            	Material material = event.getClickedBlock().getType();
    			if (material != Material.SAPLING && growthMap.containsKey(material)) {
        			event.setCancelled(true);
    			}
            }
        }
    }

	/**
	 * Determines if a plant {@link Material | @link TreeType} will grow, given the current conditions
	 * @param m The material type of the plant
	 * @param b The block that the plant is on
	 * @return Whether the plant will grow this tick
	 */
	private boolean willGrow(Object m, Block b) {
		if(growthMap.containsKey(m)) {
			boolean willGrow = Math.random() < growthMap.get(m).getRate(b);
			return willGrow;
		}
		return true;
	}
	
	@EventHandler
	public void onChunkLoad(ChunkLoadEvent e) {
		if (!plugin.persistConfig.enabled)
			return;
		
		// make sure the chunk is loaded
		Chunk chunk = e.getChunk();
		int w = WorldID.getPID(e.getChunk().getWorld().getUID());
		Coords coords = new Coords(w, chunk.getX(), 0, chunk.getZ());
		plugin.getPlantManager().loadChunk(coords);
		
		// TESTING
		//this.plugin.getLogger().info("ChunkLoaded: " + coords);

		
	}
	
	@EventHandler
	public void onChunkUnload(ChunkUnloadEvent e) {
		if (!plugin.persistConfig.enabled)
			return;
		
		Chunk chunk = e.getChunk();
		int w = WorldID.getPID(e.getChunk().getWorld().getUID());
		Coords coords = new Coords(w, chunk.getX(), 0, chunk.getZ());
		plugin.getPlantManager().minecraftChunkUnloaded(coords);
		
		// TESTING
		//this.plugin.getLogger().info("ChunkUnLoaded: " + coords);

	}
	
	@EventHandler
	public void onBlockPlace(BlockPlaceEvent event) {
		if (!plugin.persistConfig.enabled)
			return;
		
		// if the block placed was a recognized crop, register it with the manager
		Block block = event.getBlockPlaced();
		GrowthConfig growthConfig = growthMap.get(block.getType());
		if (growthConfig == null)
			return;	
		
		int w = WorldID.getPID(block.getWorld().getUID());
		plugin.getPlantManager().add(new Coords(w, block.getX(), block.getY(), block.getZ()), new Plant(System.currentTimeMillis() / 1000L));
	}
}
