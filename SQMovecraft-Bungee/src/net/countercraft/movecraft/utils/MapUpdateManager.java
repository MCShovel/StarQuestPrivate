/*
 * This file is part of Movecraft.
 *
 *     Movecraft is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Movecraft is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Movecraft.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.countercraft.movecraft.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.async.AsyncManager;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.utils.datastructures.InventoryTransferHolder;
import net.countercraft.movecraft.utils.datastructures.SignTransferHolder;
import net.countercraft.movecraft.utils.datastructures.StorageCrateTransferHolder;
import net.countercraft.movecraft.utils.datastructures.TransferData;
import net.minecraft.server.v1_7_R4.ChunkCoordIntPair;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.craftbukkit.v1_7_R4.CraftChunk;
import org.bukkit.craftbukkit.v1_7_R4.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_7_R4.util.CraftMagicNumbers;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class MapUpdateManager extends BukkitRunnable {
        private final HashMap<World, ArrayList<MapUpdateCommand>> updates = new HashMap<World, ArrayList<MapUpdateCommand>>();
        private final HashMap<World, ArrayList<EntityUpdateCommand>> entityUpdates = new HashMap<World, ArrayList<EntityUpdateCommand>>();
        public final int[] fragileBlocks = new int[]{ 29, 33, 34, 50, 52, 55, 63, 65, 68, 69, 70, 71, 72, 75, 76, 77, 93, 94, 96, 131, 132, 143, 147, 148, 149, 150, 151, 171, 323, 324, 330, 331, 356, 404 };
        final net.minecraft.server.v1_7_R4.Block AIR_ID;
        
        private MapUpdateManager() {
            Arrays.sort(fragileBlocks);
            AIR_ID = CraftMagicNumbers.getBlock(0);
        }

        public static MapUpdateManager getInstance() {
                return MapUpdateManagerHolder.INSTANCE;
        }

        private static class MapUpdateManagerHolder {
                private static final MapUpdateManager INSTANCE = new MapUpdateManager();
        }
        
        private void updateBlock(MapUpdateCommand m, ArrayList<Chunk> chunkList, World w, Map<MovecraftLocation, TransferData> dataMap, Set<net.minecraft.server.v1_7_R4.Chunk> chunks, Set<Chunk> cmChunks/*, boolean placeDispensers*/) {
                MovecraftLocation workingL = m.getNewBlockLocation();

                int x = workingL.getX();
                int y = workingL.getY();
                int z = workingL.getZ();
                Chunk chunk=null;
        
                // Calculate chunk if necessary, check list of chunks already loaded first
                
                Block b = w.getBlockAt(x , y, z);                
                boolean foundChunk=false;
                for (Chunk testChunk : chunkList) {
                        int sx=x>>4;
                        int sz=z>>4;
                        if((testChunk.getX()==sx)&&(testChunk.getZ()==sz)) {
                                foundChunk=true;
                                chunk=testChunk;
                        }
                }
                if(!foundChunk) {
                        chunk = b.getChunk();
                        chunkList.add(chunk);
                        if(!chunk.isLoaded()){
                        	chunk.load();
                        }
                }

                net.minecraft.server.v1_7_R4.Chunk c = null;
                Chunk cmC = null;
                if(Settings.CompatibilityMode) {
                        cmC = chunk;
                } else {
                        c = ( ( CraftChunk ) chunk ).getHandle();
                }

                //get the inner-chunk index of the block to change
                //modify the block in the chunk

                int newTypeID = m.getTypeID();
                /*if(newTypeID==23 && !placeDispensers) {
                        newTypeID=1;
                }*/
                TransferData transferData = dataMap.get( workingL );

                byte data;
                if ( transferData != null ) {
                        data = transferData.getData();
                } else {
                        data = 0;
                }
                
                int origType=b.getTypeId();
                
                boolean success = false;
                
                if(m.shouldDrill()){
                	World wrld = m.getCraft().getW();
                	Location l = b.getLocation().add(0.5, 1.5, 0.5);
                	for(ItemStack i : b.getDrops()){
                		wrld.dropItem(l, i);
                	}
                }
                
                //don't blank out block if it's already air, or if blocktype will not be changed
                if(Settings.CompatibilityMode) {
                        if((origType!=0)&&(origType!=newTypeID)) {
                                b.setTypeIdAndData( 0, (byte) 0, false );
                        } 
                        b.setTypeIdAndData( newTypeID, data, false );
                        if ( !cmChunks.contains( cmC ) ) {
                                cmChunks.add( cmC );
                        }
                } else {
                        if((origType!=0)&&(origType!=newTypeID)) {
                                c.a( x & 15, y, z & 15, AIR_ID, 0 );
                        	    //w.getBlockAt( x, y, z ).setTypeIdAndData( 0, (byte) 0, false );
                        } 
                        success = c.a( x & 15, y, z & 15, CraftMagicNumbers.getBlock(newTypeID), data );
                        //w.getBlockAt( x, y, z ).setTypeIdAndData( newTypeID, data, false );
                        if ( !success ) {
                                b.setTypeIdAndData( newTypeID, data, false );
                        }
                        if ( !chunks.contains( c ) ) {
                                chunks.add( c );
                        }
                }
				if (m.isLastUpdate()){
					AsyncManager.getInstance().clear(m.getCraft());
				}  
        }

        public void run() {
                if ( updates.isEmpty() ) return;
                
                for ( World w : updates.keySet() ) {
                        if ( w != null ) {
                                List<MapUpdateCommand> updatesInWorld = updates.get( w );
                                List<EntityUpdateCommand> entityUpdatesInWorld = entityUpdates.get( w );
                                Map<MovecraftLocation, List<EntityUpdateCommand>> entityMap = new HashMap<MovecraftLocation, List<EntityUpdateCommand>>();
                                Map<MovecraftLocation, TransferData> dataMap = new HashMap<MovecraftLocation, TransferData>();
                                Set<net.minecraft.server.v1_7_R4.Chunk> chunks = null; 
                                Set<Chunk> cmChunks = null;
                                if(Settings.CompatibilityMode) {
                                        cmChunks = new HashSet<Chunk>();                                        
                                } else {
                                        chunks = new HashSet<net.minecraft.server.v1_7_R4.Chunk>();
                                }

                                // Preprocessing
                                for ( MapUpdateCommand c : updatesInWorld ) {
                                        MovecraftLocation l = c.getOldBlockLocation();

                                        if ( l != null ) {
                                                TransferData blockDataPacket = getBlockDataPacket( w.getBlockAt( l.getX(), l.getY(), l.getZ() ).getState(), c.getRotation(), c.getCraft());
                                                if ( blockDataPacket != null ) {
                                                        dataMap.put( c.getNewBlockLocation(), blockDataPacket );
                                                        if (c.isLastUpdate()){
                            								blockDataPacket.isLastUpdate = true;
                            								c.setLastUpdate(false);
                            							}
                                                }
                                                //remove dispensers and replace them with stone blocks to prevent firing during ship reconstruction
                                                /*if(w.getBlockAt( l.getX(), l.getY(), l.getZ() ).getTypeId()==23) {
                                                        w.getBlockAt( l.getX(), l.getY(), l.getZ() ).setTypeIdAndData( 1, (byte) 0, false );
                                                }*/
                                        }

                                } 
                                // track the blocks that entities will be standing on to move them smoothly with the craft
                                if(entityUpdatesInWorld!=null) {
                                        for( EntityUpdateCommand i : entityUpdatesInWorld) {
                                                if(i!=null) {
                                                        MovecraftLocation entityLoc=new MovecraftLocation(i.getNewLocation().getBlockX(), i.getNewLocation().getBlockY()-1, i.getNewLocation().getBlockZ());
                                                        if(!entityMap.containsKey(entityLoc)) {
                                                                List<EntityUpdateCommand> entUpdateList=new ArrayList<EntityUpdateCommand>();
                                                                entUpdateList.add(i);
                                                                entityMap.put(entityLoc, entUpdateList);
                                                        } else {
                                                                List<EntityUpdateCommand> entUpdateList=entityMap.get(entityLoc);
                                                                entUpdateList.add(i);
                                                        }
                                                }
                                        }
                                }
                                
                                ArrayList<Chunk> chunkList = new ArrayList<Chunk>();
                                
                                // Perform core block updates, don't do "fragiles" yet. Don't do Dispensers yet either
                                for ( MapUpdateCommand m : updatesInWorld ) {
                                        boolean isFragile=(Arrays.binarySearch(fragileBlocks,m.getTypeID())>=0);
                                        
                                        if(!isFragile) {
                                                updateBlock(m, chunkList, w, dataMap, chunks, cmChunks);
                                        }
                                        
                                        // if the block you just updated had any entities on it, move them. If they are moving, add in their motion to the craft motion
                                        if( entityMap.containsKey(m.getNewBlockLocation()) ) {
                							List<EntityUpdateCommand> mapUpdateList=entityMap.get(m.getNewBlockLocation());
                							for(EntityUpdateCommand entityUpdate : mapUpdateList) {
                								Entity entity=entityUpdate.getEntity();
                								Vector pVel=new Vector(entity.getVelocity().getX(),0.0,entity.getVelocity().getZ());
                							/*	Location newLoc=entity.getLocation();
                								newLoc.setX(entityUpdate.getNewLocation().getX());
                								newLoc.setY(entityUpdate.getNewLocation().getY());
                								newLoc.setZ(entityUpdate.getNewLocation().getZ());*/
                								entity.teleport(entityUpdate.getNewLocation());
                								entity.setVelocity(pVel);
                							}
                							entityMap.remove(m.getNewBlockLocation());
                						}
                                        if(m.isLastUpdate()){
                                        	AsyncManager.getInstance().clear(m.getCraft());
                                        }
                                }

                                // Fix redstone and other "fragiles"                                
                                for ( MapUpdateCommand i : updatesInWorld ) {
                                        boolean isFragile=(Arrays.binarySearch(fragileBlocks,i.getTypeID())>=0);
                                        if(isFragile) {
                                                updateBlock(i, chunkList, w, dataMap, chunks, cmChunks);
                                        }
                                }

                                /*// Put Dispensers back in now that the ship is reconstructed
                                for ( MapUpdateCommand i : updatesInWorld ) {
                                        if(i.getTypeID()==23) {
                                                updateBlock(i, chunkList, w, dataMap, chunks, cmChunks);
                                        }
                                }*/

                                // Restore block specific information
                                for ( MovecraftLocation l : dataMap.keySet() ) {
                                        try {
                                                TransferData transferData = dataMap.get( l );
                                                Block b = w.getBlockAt(l.getX(), l.getY(), l.getZ());

                                                if ( transferData instanceof SignTransferHolder ) {

                                                        SignTransferHolder signData = ( SignTransferHolder ) transferData;
                                                        Sign sign = ( Sign ) b.getState();
                                                        for ( int i = 0; i < signData.getLines().length; i++ ) {
                                                                sign.setLine( i, signData.getLines()[i] );
                                                        }
                                                        sign.update( true );

                                                } else if ( transferData instanceof StorageCrateTransferHolder ) {
                                                       /* Inventory inventory = Bukkit.createInventory( null, 27, String.format( I18nSupport.getInternationalisedString( "Item - Storage Crate name" ) ) );
                                                        inventory.setContents( ( ( StorageCrateTransferHolder ) transferData ).getInvetory() );
                                                        StorageChestItem.setInventoryOfCrateAtLocation( inventory, l, w );*/

                                                } else if ( transferData instanceof InventoryTransferHolder ) {

                                                        InventoryTransferHolder invData = ( InventoryTransferHolder ) transferData;
                                                        InventoryHolder inventoryHolder = ( InventoryHolder ) w.getBlockAt( l.getX(), l.getY(), l.getZ() ).getState();
                                                        inventoryHolder.getInventory().setContents( invData.getInvetory() );

                                                }
                                                b.setData( transferData.getData() );
                                                if (transferData.isLastUpdate){
                                                	AsyncManager.getInstance().clear(transferData.c);
                        						}
                                        } catch ( Exception e ) {
                                                Movecraft.getInstance().getLogger().log( Level.SEVERE, "Severe error in map updater" );
                                                e.printStackTrace();
                                        }

                                }
                                
                                if(Settings.CompatibilityMode) {
                                        // todo: lighting stuff here
                                } else {
                                        for ( net.minecraft.server.v1_7_R4.Chunk c : chunks ) {
                                                c.initLighting();
                                                ChunkCoordIntPair ccip = new ChunkCoordIntPair( c.locX, c.locZ );


                                                for ( Player p : w.getPlayers() ) {
                                                        List<ChunkCoordIntPair> chunkCoordIntPairQueue = ( List<ChunkCoordIntPair> ) ( ( CraftPlayer ) p ).getHandle().chunkCoordIntPairQueue;

                                                        if ( !chunkCoordIntPairQueue.contains( ccip ) )
                                                                chunkCoordIntPairQueue.add( ccip );
                                                }
                                        }
                                }

                                
                                // finally clean up dropped items that are fragile block types on or below all crafts. They are likely garbage left on the ground from the block movements
                                if(CraftManager.getInstance().getCraftsInWorld(w)!=null) {
                                        for(Craft cleanCraft : CraftManager.getInstance().getCraftsInWorld(w)) {
                                                Iterator<Entity> i=w.getEntities().iterator();
                                                while (i.hasNext()) {
                                                        Entity eTest=i.next();
                                                        if (eTest.getTicksLived()<100 && eTest.getType()==org.bukkit.entity.EntityType.DROPPED_ITEM) {
                                                                int adjX=eTest.getLocation().getBlockX()-cleanCraft.getMinX();
                                                                int adjZ=eTest.getLocation().getBlockZ()-cleanCraft.getMinZ();
                                                                int[][][] hb=cleanCraft.getHitBox();
                                                                if(adjX>=-1 && adjX<=hb.length) {
                                                                        if(adjX<0) {
                                                                                adjX=0;
                                                                        }
                                                                        if(adjX>=hb.length) {
                                                                                adjX=hb.length-1;
                                                                        }
                                                                        if(adjZ>-1 && adjZ<=hb[adjX].length) {
                                                                                Item it=(Item)eTest;

                                                                                if(Arrays.binarySearch(fragileBlocks,it.getItemStack().getTypeId())>=0) {
                                                                                        eTest.remove();
                                                                                }
                                                                        }
                                                                }
                                                        }
                                                }
                                        }        
                                }
                        }
                }

                
                updates.clear();
                entityUpdates.clear();
        }

        public boolean addWorldUpdate( World w, MapUpdateCommand[] mapUpdates, EntityUpdateCommand[] eUpdates) {
                ArrayList<MapUpdateCommand> get = updates.get( w );
                if ( get != null ) {
                        updates.remove( w );
                } else {
                        get = new ArrayList<MapUpdateCommand>();
                }

                ArrayList<MapUpdateCommand> tempSet = new ArrayList<MapUpdateCommand>();
                if(mapUpdates != null){
	                for ( MapUpdateCommand m : mapUpdates ) {
	
	                        if ( setContainsConflict( get, m ) ) {
	                                return true;
	                        } else {
	                                tempSet.add( m );
	                        }
	
	                }
                }
                get.addAll( tempSet );
                updates.put( w, get );

                //now do entity updates
                if(eUpdates!=null) {
                        ArrayList<EntityUpdateCommand> tempEUpdates = new ArrayList<EntityUpdateCommand>();
                        for(EntityUpdateCommand e : eUpdates) {
                                tempEUpdates.add(e);
                        }

                        entityUpdates.put(w, tempEUpdates);
                }                
                return false;
        }

        private boolean setContainsConflict( ArrayList<MapUpdateCommand> set, MapUpdateCommand c ) {
                for ( MapUpdateCommand command : set ) {
                        if ( command.getNewBlockLocation().equals( c.getNewBlockLocation() ) ) {
                                return true;
                        }
                }

                return false;
        }

        private boolean arrayContains( int[] oA, int o ) {
                for ( int testO : oA ) {
                        if ( testO == o ) {
                                return true;
                        }
                }

                return false;
        }

        private TransferData getBlockDataPacket( BlockState s, Rotation r, Craft c ) {
                if ( BlockUtils.blockHasNoData( s.getTypeId() ) ) {
                        return null;
                }

                byte data = s.getRawData();

                if ( BlockUtils.blockRequiresRotation( s.getTypeId() ) && r != Rotation.NONE ) {
                        data = BlockUtils.rotate( data, s.getTypeId(), r );
                }

                switch ( s.getTypeId() ) {
                        case 23:
                        case 61:
                        case 62:
                        case 117:
                        case 154:
                        case 158:
                                // Data and Inventory
                                ItemStack[] contents = ( ( InventoryHolder ) s ).getInventory().getContents().clone();
                                ( ( InventoryHolder ) s ).getInventory().clear();
                                return new InventoryTransferHolder( data, contents, c);

                        case 68:
                        case 63:
                                // Data and sign lines
                                return new SignTransferHolder( data, ( ( Sign ) s ).getLines(), c );

                        case 33:
                                /*MovecraftLocation l = MathUtils.bukkit2MovecraftLoc( s.getLocation() );
                                Inventory i = StorageChestItem.getInventoryOfCrateAtLocation( l, s.getWorld() );
                                if ( i != null ) {
                                        StorageChestItem.removeInventoryAtLocation( s.getWorld(), l );
                                        return new StorageCrateTransferHolder( data, i.getContents() );
                                } else {
                                        return new TransferData( data );
                                }*/

                        default:
                                return new TransferData( data, c);

                }
        }

}