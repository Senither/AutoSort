package plugin.arcwolf.autosort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Furnace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import plugin.arcwolf.autosort.Network.NetworkItem;
import plugin.arcwolf.autosort.Network.SortChest;
import plugin.arcwolf.autosort.Network.SortNetwork;
import plugin.arcwolf.lavafurnace.FurnaceObject;
import plugin.arcwolf.lavafurnace.LavaFurnace;

public class AutoSortListener implements Listener {

    private AutoSort plugin;
    public Map<String, SortNetwork> chestLock = new Hashtable<String, SortNetwork>();

    public AutoSortListener(AutoSort autoSort) {
        plugin = autoSort;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;
        Block block = event.getBlock();
        Player player = event.getPlayer();
        if (block.getType().equals(Material.HOPPER)) {
            List<Block> blocksToTest = getBlocksToTest(block.getData(), block);
            if (hopperDropperStopper(blocksToTest, player)) {
                event.setCancelled(true);
                return;
            }
        }
        else if (block.getType().equals(Material.DROPPER)) {
            List<Block> blocksToTest = new ArrayList<Block>();
            blocksToTest.add(block.getRelative(BlockFace.UP));
            blocksToTest.add(block.getRelative(BlockFace.NORTH));
            blocksToTest.add(block.getRelative(BlockFace.SOUTH));
            blocksToTest.add(block.getRelative(BlockFace.WEST));
            blocksToTest.add(block.getRelative(BlockFace.EAST));
            blocksToTest.add(block.getRelative(BlockFace.DOWN));
            if (hopperDropperStopper(blocksToTest, player)) {
                event.setCancelled(true);
                return;
            }
        }
        else if (block.getType().equals(Material.CHEST) || block.getType().equals(Material.TRAPPED_CHEST)) {
            if (doubleChestPlaceChest(Material.CHEST, block, player) || doubleChestPlaceChest(Material.TRAPPED_CHEST, block, player)) {
                event.setCancelled(true);
                return;
            }
            Block hopper = findHopper(block);
            if (hopper.getType().equals(Material.HOPPER)) {
                List<Block> blocksToTest = getBlocksToTest(hopper.getData(), hopper);
                for(Block blockToTest : blocksToTest)
                    if (block.getLocation().equals(blockToTest.getLocation())) {
                        List<Block> testBlock = new ArrayList<Block>();
                        testBlock.add(hopper);
                        if (hopperDropperStopper(testBlock, player))
                            event.setCancelled(true);
                        return;
                    }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (event.isCancelled()) return;
        Item item = (Item) event.getEntity();
        plugin.items.add(item);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        CustomPlayer settings = CustomPlayer.getSettings(player);
        plugin.util.restoreWithdrawnInv(settings, player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.isCancelled()) return;
        InventoryHolder holder = event.getInventory().getHolder();
        Block block = null;
        Block lChest = null;
        Block rChest = null;
        SortNetwork sortNetwork = null;
        Player player = null;
        if (event.getPlayer() instanceof Player)
            player = (Player) event.getPlayer();
        else
            return;
        CustomPlayer settings = CustomPlayer.getSettings(player);
        if (holder instanceof DoubleChest) {
            DoubleChest dblchest = (DoubleChest) holder;
            lChest = ((Chest) dblchest.getLeftSide()).getBlock();
            rChest = ((Chest) dblchest.getRightSide()).getBlock();
            sortNetwork = plugin.allNetworkBlocks.get(lChest);
            if (sortNetwork == null) sortNetwork = plugin.allNetworkBlocks.get(rChest);
            if (sortNetwork == null) return;
            if (sortNetwork.withdrawChests.containsKey(lChest)) {
                settings.block = lChest;
                settings.withdrawInventory = ((Chest) lChest.getState()).getInventory();
                block = lChest;
            }
            else if (sortNetwork.withdrawChests.containsKey(rChest)) {
                settings.block = rChest;
                settings.withdrawInventory = ((Chest) rChest.getState()).getInventory();
                block = rChest;
            }
            else
                return;
        }
        else if (holder instanceof Chest) {
            block = ((Chest) holder).getBlock();
            sortNetwork = plugin.allNetworkBlocks.get(block);
            if (sortNetwork == null) return;
            if (sortNetwork.withdrawChests.containsKey(block)) {
                settings.block = block;
                settings.withdrawInventory = ((Chest) block.getState()).getInventory();
            }
            else
                return;
        }
        if (block == null || player == null || sortNetwork == null) return;
        String netName = sortNetwork.netName;
        String owner = sortNetwork.owner;

        //Transaction Start
        chestLock.put(player.getName(), sortNetwork);
        settings.netName = netName;
        settings.owner = owner;
        settings.playerName = player.getName();
        settings.sortNetwork = sortNetwork;
        if (plugin.util.updateInventoryList(player, settings)) {
            Collections.sort(settings.inventory, new IntegerComparator());
            plugin.util.updateChestInventory(player, settings);
        }
        else {
            player.sendMessage("The network - " + ChatColor.YELLOW + netName + ChatColor.WHITE + " - is empty.");
            chestLock.remove(player.getName());
            settings.clearPlayer();
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        int clickedId = event.getRawSlot();
        Player player = (Player) event.getWhoClicked();
        CustomPlayer settings = CustomPlayer.getSettings(player);
        if (chestLock.containsKey(player.getName())) {
            if (clickedId == 0 || clickedId == 8) { // If Player clicks on slot 0 or 8 advance or reverse inventory view
                if (event.getCursor() != null) event.setCancelled(true);
                int maxStartIdx = settings.inventory.size();
                if (clickedId == 0) {
                    if (settings.startItemIdx >= 1)
                        settings.startItemIdx--;
                    else
                        settings.startItemIdx = maxStartIdx - 1;
                }
                else if (clickedId == 8) {
                    if (settings.startItemIdx < maxStartIdx - 1)
                        settings.startItemIdx++;
                    else
                        settings.startItemIdx = 0;
                }
                plugin.util.updateChestTask(player, settings);
            }
            else if (clickedId <= settings.withdrawInventory.getSize()) { // If player removes item from chest resort chest
                plugin.util.updateChestTask(player, settings);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.isCancelled()) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Player player = event.getPlayer();
        String pName = player.getName();
        if (plugin.util.isValidInventoryBlock(player, block, false) || isValidSign(block)) {
            SortNetwork sortNetwork = plugin.allNetworkBlocks.get(block);
            if (sortNetwork == null) return;
            if (!pName.equalsIgnoreCase(sortNetwork.owner) && !sortNetwork.members.contains(pName) && !plugin.playerHasPermission(player, "autosort.override")) {
                //Transaction Fail isnt owned by this player
                player.sendMessage("This network is owned by " + ChatColor.YELLOW + sortNetwork.owner);
                player.sendMessage(ChatColor.RED + "You can not access or modify this Network.");
                event.setCancelled(true);
            }
            else if (chestLock.containsValue(sortNetwork)) {
                if (!(sortNetwork.withdrawChests.containsKey(block) || sortNetwork.withdrawChests.containsKey(plugin.util.doubleChest(block)))) return;

                String user = "";
                for(Entry<String, SortNetwork> sortNet : chestLock.entrySet()) {
                    if (sortNet.getValue().equals(sortNetwork)) {
                        user = sortNet.getKey();
                        break;
                    }
                }
                //Transaction Fail someone else is using the withdraw function
                player.sendMessage("This network is being withdrawn from by " + ChatColor.YELLOW + user);
                player.sendMessage(ChatColor.GOLD + "Please wait...");
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        String playerName = "";
        CustomPlayer settings = CustomPlayer.getSettings(player);
        playerName = settings.playerName;
        chestLock.remove(playerName);
        settings.clearPlayer();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String playerName = "";
        CustomPlayer settings = CustomPlayer.getSettings(player);
        playerName = settings.playerName;
        if (playerName != "") {
            plugin.util.restoreWithdrawnInv(settings, player);
        }
    }

    // TODO Thats a little better... still think it could use a little more cleanup though...
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSignChange(SignChangeEvent event) {
        if (event.isCancelled()) return;
        String[] lines = event.getLines();
        String netName = "";
        Player player = event.getPlayer();

        SortNetwork sortNetwork = null;
        if (lines[0].startsWith("#") || lines[0].startsWith("*")) {
            netName = lines[0].substring(1);
            sortNetwork = plugin.findNetwork(player.getName(), netName);
            if (sortNetwork == null && plugin.playerHasPermission(player, "autosort.create"))
                sortNetwork = createNetwork(player, netName);
            else if (!plugin.playerHasPermission(player, "autosort.create")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to create AutoSort networks!" + plugin.playerHasPermission(player, "autosort.create"));
                return;
            }
        }
        if (netName.equals("")) return;

        if (event.getBlock().getType().equals(Material.WALL_SIGN)) {
            Block signBlock = event.getBlock();
            if (lines[0].startsWith("#")) { //TODO Withdraw Chest
                if (plugin.playerHasPermission(player, "autosort.use.withdraw")) {
                    String option = lines[3].toUpperCase();
                    Block storageBlock = getDirection("", signBlock);
                    if (option.startsWith("D:")) {
                        storageBlock = getDirection(option.split(":")[1], signBlock);
                    }
                    if (plugin.util.isValidWithdrawBlock(player, storageBlock, true) && !isInNetwork(player, storageBlock)) {
                        if (!AutoSort.worldRestrict || sortNetwork.world.equalsIgnoreCase(signBlock.getWorld().getName().toLowerCase())) {
                            int prox = getProximity(netName);
                            Location origin = getOrigin(sortNetwork.sortChests);
                            Location here = storageBlock.getLocation();
                            if (prox == 0 || (origin != null && origin.distance(here) <= prox) || plugin.playerHasPermission(player, "autosort.ignoreproximity")) {
                                NetworkItem netItem = new NetworkItem(sortNetwork, storageBlock, signBlock);
                                sortNetwork.withdrawChests.put(storageBlock, netItem);
                                plugin.allNetworkBlocks.put(signBlock, sortNetwork);
                                plugin.allNetworkBlocks.put(storageBlock, sortNetwork);
                                plugin.allNetworkBlocks.put(plugin.util.doubleChest(storageBlock), sortNetwork);
                                event.setLine(1, "�fOpen Chest");
                                event.setLine(2, "�fTo Withdraw");
                                player.sendMessage(ChatColor.AQUA + "Withdraw chest added to network " + netName + ".");
                            }
                            else {
                                player.sendMessage(ChatColor.RED + "You can only place chests within " + prox + " blocks of the original chest!");
                                event.setCancelled(true);
                            }
                        }
                        else {
                            player.sendMessage(ChatColor.RED + "You can't add to a network unless you are in the same world as it!");
                            event.setCancelled(true);
                        }
                    }
                    else {
                        event.setCancelled(true);
                        return;
                    }
                }
                else {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "You do not have permission to create Withdraw chests.");
                }
            }
            else if (lines[0].startsWith("*")) {
                if (event.getPlayer().hasPermission("autosort.use")) {
                    String mat1 = lines[1].toUpperCase();
                    String mat2 = lines[2].toUpperCase();

                    String[] options = { lines[3].toUpperCase() };
                    if (lines[3].contains(" ")) {
                        options = lines[3].toUpperCase().split(" ");
                    }
                    int priority = 2;
                    Block storageBlock = getDirection("", signBlock);
                    for(String opt : options) {
                        if (opt.startsWith("P:")) {
                            String pStr = opt.split(":")[1];
                            priority = getPriority(pStr);
                            if (priority == -1) {
                                event.setCancelled(true);
                                player.sendMessage(ChatColor.RED + "Invalid Priority: " + pStr);
                            }
                        }
                        else if (opt.startsWith("D:")) {
                            storageBlock = getDirection(opt.split(":")[1], signBlock);
                        }
                    }

                    String mat;
                    if (mat2.equalsIgnoreCase("")) {
                        mat = mat1;
                    }
                    else {
                        mat = mat1 + "," + mat2;
                    }

                    event.setLine(1, mat1);
                    event.setLine(2, mat2);

                    if (mat.equalsIgnoreCase("")) { //TODO Deposit Chest
                        if (plugin.util.isValidDepositBlock(player, storageBlock, true) && !isInNetwork(player, storageBlock)) {
                            if (!AutoSort.worldRestrict || sortNetwork.world.equalsIgnoreCase(signBlock.getWorld().getName().toLowerCase())) {
                                int prox = getProximity(netName);
                                Location origin = getOrigin(sortNetwork.sortChests);
                                Location here = storageBlock.getLocation();
                                if (prox == 0 || (origin != null && origin.distance(here) <= prox) || plugin.playerHasPermission(player, "autosort.ignoreproximity")) {
                                    event.setLine(1, "�fOpen Chest");
                                    event.setLine(2, "�fTo Deposit");
                                    player.sendMessage(ChatColor.AQUA + "Deposit chest added to " + sortNetwork.netName + ".");
                                    NetworkItem netItem = new NetworkItem(sortNetwork, storageBlock, signBlock);
                                    sortNetwork.depositChests.put(storageBlock, netItem);
                                    plugin.allNetworkBlocks.put(signBlock, sortNetwork);
                                    plugin.allNetworkBlocks.put(storageBlock, sortNetwork);
                                    plugin.allNetworkBlocks.put(plugin.util.doubleChest(storageBlock), sortNetwork);
                                }
                                else {
                                    player.sendMessage(ChatColor.RED + "You can only place chests within " + prox + " blocks of the original chest!");
                                    event.setCancelled(true);
                                }
                            }
                            else {
                                player.sendMessage(ChatColor.RED + "You can't add to a network unless you are in the same world as it!");
                                event.setCancelled(true);
                            }
                        }
                        else {
                            event.setCancelled(true);
                            return;
                        }
                    }
                    else { //TODO Sort Chest
                        String[] matParts = mat.split(",");
                        for(String part : matParts) {
                            if (!isValid(part)) {
                                event.getPlayer().sendMessage(ChatColor.RED + "Invalid Material: " + part);
                                event.setCancelled(true);
                                return;
                            }
                            if (!lavaFurnaceCheck(player, sortNetwork, storageBlock, part)) {
                                event.getPlayer().sendMessage(ChatColor.RED + "You are not the owner of this LavaFurnace.");
                                event.setCancelled(true);
                                return;
                            }
                        }
                        if (plugin.util.isValidInventoryBlock(player, storageBlock, true) && !isInNetwork(player, storageBlock)) {
                            boolean dd = !mat.contains(":");

                            if (plugin.playerHasPermission(player, "autosort.override") || sortNetwork.owner.equalsIgnoreCase(player.getName())) {
                                if (!AutoSort.worldRestrict || sortNetwork.world.equalsIgnoreCase(signBlock.getWorld().getName().toLowerCase())) {
                                    int prox = getProximity(netName);
                                    Location origin = getOrigin(sortNetwork.sortChests);
                                    Location here = storageBlock.getLocation();
                                    if (prox == 0 || (origin != null && origin.distance(here) <= prox) || plugin.playerHasPermission(player, "autosort.ignoreproximity")) {
                                        player.sendMessage(ChatColor.AQUA + "Deposit chest added to " + sortNetwork.netName + ".");
                                        sortNetwork.sortChests.add(new SortChest(storageBlock, signBlock, mat, priority, dd));
                                        plugin.allNetworkBlocks.put(signBlock, sortNetwork);
                                        plugin.allNetworkBlocks.put(storageBlock, sortNetwork);
                                        plugin.allNetworkBlocks.put(plugin.util.doubleChest(storageBlock), sortNetwork);
                                        player.sendMessage(ChatColor.AQUA + "Sort chest with material(s) " + mat + " and priority " + priority + " added to network " + netName + ".");
                                    }
                                    else {
                                        player.sendMessage(ChatColor.RED + "You can only place chests within " + prox + " blocks of the original chest!");
                                        event.setCancelled(true);
                                    }
                                }
                                else {
                                    player.sendMessage(ChatColor.RED + "You can't add to a network unless you are in the same world as it!");
                                    event.setCancelled(true);
                                }
                            }
                            else {
                                player.sendMessage(ChatColor.RED + "You don't have permission to use that network!");
                                event.setCancelled(true);
                            }
                        }
                        else {
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
                else {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(ChatColor.RED + "You do not have permission to create AutoSort chests.");
                }
            }
        }
        else if (event.getBlock().getType().equals(Material.SIGN_POST)) { // TODO Drop Sign
            if (plugin.playerHasPermission(player, "autosort.use")) {
                if (lines[0].startsWith("*")) {
                    Block sign = event.getBlock();
                    if (!AutoSort.worldRestrict || sortNetwork.world.equalsIgnoreCase(sign.getWorld().getName().toLowerCase())) {
                        int prox = getProximity(netName);
                        Location origin = getOrigin(sortNetwork.sortChests);
                        Location here = sign.getLocation();
                        if (prox == 0 || (origin != null && origin.distance(here) <= prox) || plugin.playerHasPermission(player, "autosort.ignoreproximity")) {
                            NetworkItem netItem = new NetworkItem(sortNetwork, null, sign);
                            sortNetwork.dropSigns.put(sign, netItem);
                            plugin.allNetworkBlocks.put(sign, sortNetwork);
                            player.sendMessage(ChatColor.BLUE + "Drop Sign added to network " + netName + ".");
                            event.setLine(1, "�fDrop Items");
                            event.setLine(2, "�fOn Sign");
                        }
                        else {
                            player.sendMessage(ChatColor.RED + "You can only place drop signs within " + prox + " blocks of the original chest!");
                            event.setCancelled(true);
                        }
                    }
                    else {
                        player.sendMessage(ChatColor.RED + "You can't add to a network unless you are in the same world as it!");
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        if (event.isCancelled()) return;
        Block block = event.getBlock();
        SortNetwork sortNetwork = plugin.allNetworkBlocks.get(block);
        if (sortNetwork == null) return;
        if (sortNetwork.depositChests.containsKey(block)) {
            NetworkItem netItem = sortNetwork.depositChests.get(block);
            Sign sign = (Sign) netItem.sign.getState();
            if (sign.getLine(0).startsWith("*")) {
                ItemStack is = event.getResult();
                if (sortNetwork.sortItem(is)) {
                    event.setCancelled(true);
                    if (event.getSource().getAmount() > 1) {
                        event.getSource().setAmount(event.getSource().getAmount() - 1);
                    }
                    else {
                        ((Furnace) block.getState()).getInventory().remove(event.getSource());
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (event.isCancelled()) return;
        Block block = event.getBlock();
        if (block.getType().equals(Material.WALL_SIGN) || block.getType().equals(Material.SIGN_POST)) {
            String[] lines = ((Sign) block.getState()).getLines();
            if (lines[0].startsWith("*") || lines[0].startsWith("#")) {
                if (plugin.allNetworkBlocks.containsKey(block)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        Block block = event.getBlock();
        Sign sign = null;
        Player player = event.getPlayer();
        String pName = player.getName();
        SortNetwork network = null;
        if (block.getType().equals(Material.SIGN_POST)) {
            network = plugin.allNetworkBlocks.get(block);
            if (network == null) return;
            if (network.owner.equals(pName) || plugin.playerHasPermission(player, "autosort.override")) {
                // Drop Sign
                network.dropSigns.remove(block);
                plugin.allNetworkBlocks.remove(block);
                network.dropSigns.remove(block);
                event.getPlayer().sendMessage(ChatColor.BLUE + "Drop sign removed.");
                return;
            }
            else {
                event.getPlayer().sendMessage("This network is owned by " + ChatColor.YELLOW + network.owner);
                event.getPlayer().sendMessage(ChatColor.RED + "You can't modify this network.");
                event.setCancelled(true);
                return;
            }
        }
        else if (block.getType().equals(Material.WALL_SIGN)) {
            network = plugin.allNetworkBlocks.get(block);
            if (network == null) return;
            if (network.owner.equals(pName) || plugin.playerHasPermission(player, "autosort.override")) {
                sign = (Sign) block.getState();
                String[] lines = sign.getLines();
                Block storageBlock = getDirection("", block);
                String[] options = { lines[3].toUpperCase() };
                if (lines[3].contains(" ")) {
                    options = lines[3].toUpperCase().split(" ");
                }
                for(String opt : options) {
                    if (opt.startsWith("D:")) {
                        storageBlock = getDirection(opt.split(":")[1], block);
                    }
                }
                if (lines[0].startsWith("*")) {
                    // Deposit or Sort Chest
                    if (lines[1].equals("�fOpen Chest")) {
                        // Deposit Chest
                        plugin.allNetworkBlocks.remove(block);
                        plugin.allNetworkBlocks.remove(storageBlock);
                        plugin.allNetworkBlocks.remove(plugin.util.doubleChest(storageBlock));
                        network.depositChests.remove(storageBlock);
                        network.depositChests.remove(plugin.util.doubleChest(storageBlock));
                        event.getPlayer().sendMessage(ChatColor.BLUE + "Deposit chest removed.");
                        return;
                    }
                    else {
                        // Sort Chest
                        SortChest sc = network.findSortChest(storageBlock);
                        if (sc == null) return;
                        plugin.allNetworkBlocks.remove(sc.block);
                        plugin.allNetworkBlocks.remove(plugin.util.doubleChest(sc.block));
                        plugin.allNetworkBlocks.remove(sc.sign);
                        network.sortChests.remove(sc);
                        event.getPlayer().sendMessage(ChatColor.BLUE + "Sort chest removed.");
                        return;
                    }
                }
                else if (lines[0].startsWith("#")) {
                    // Withdraw Chest
                    if (chestLock.containsValue(network)) {
                        String user = "";
                        for(Entry<String, SortNetwork> sortNet : chestLock.entrySet()) {
                            if (sortNet.getValue().equals(network)) {
                                user = sortNet.getKey();
                                break;
                            }
                        }
                        //Transaction Fail someone else is using the withdraw function
                        player.sendMessage("This network is being withdrawn from by " + ChatColor.YELLOW + user);
                        player.sendMessage(ChatColor.GOLD + "Please wait...");
                        event.setCancelled(true);
                        return;
                    }
                    else {
                        plugin.allNetworkBlocks.remove(block);
                        plugin.allNetworkBlocks.remove(storageBlock);
                        plugin.allNetworkBlocks.remove(plugin.util.doubleChest(storageBlock));
                        network.withdrawChests.remove(storageBlock);
                        network.withdrawChests.remove(plugin.util.doubleChest(storageBlock));
                        event.getPlayer().sendMessage(ChatColor.BLUE + "Withdraw chest removed.");
                        return;
                    }
                }
            }
            else {
                event.getPlayer().sendMessage("This network is owned by " + ChatColor.YELLOW + network.owner);
                event.getPlayer().sendMessage(ChatColor.RED + "You can't modify this network.");
                event.setCancelled(true);
                return;
            }
        }
        else if (plugin.util.isValidInventoryBlock(player, block, false)) {
            network = plugin.allNetworkBlocks.get(block);
            if (network == null) network = plugin.allNetworkBlocks.get(plugin.util.doubleChest(block));
            if (network == null) return;
            if (network.owner.equals(pName) || plugin.playerHasPermission(player, "autosort.override")) {

                if (network.depositChests.containsKey(block) || network.depositChests.containsKey(plugin.util.doubleChest(block))) {
                    NetworkItem netItem = network.depositChests.get(block);
                    if (netItem == null) netItem = network.depositChests.get(plugin.util.doubleChest(block));
                    if (netItem == null) return;
                    Block signBlock = netItem.sign;
                    if (signBlock.getType().equals(Material.WALL_SIGN) && signBlock.getLocation().getBlock().getType().equals(Material.WALL_SIGN)) {
                        Sign chestSign = (Sign) signBlock.getState();
                        for(int line = 0; line < 4; line++)
                            chestSign.setLine(line, "");
                        chestSign.update();
                    }
                    plugin.allNetworkBlocks.remove(block);
                    plugin.allNetworkBlocks.remove(plugin.util.doubleChest(block));
                    plugin.allNetworkBlocks.remove(signBlock);
                    network.depositChests.remove(block);
                    network.depositChests.remove(plugin.util.doubleChest(block));
                    event.getPlayer().sendMessage(ChatColor.BLUE + "Deposit chest removed.");
                }
                else if (network.withdrawChests.containsKey(block) || network.withdrawChests.containsKey(plugin.util.doubleChest(block))) {
                    if (chestLock.containsValue(network)) {
                        String user = "";
                        for(Entry<String, SortNetwork> sortNet : chestLock.entrySet()) {
                            if (sortNet.getValue().equals(network)) {
                                user = sortNet.getKey();
                                break;
                            }
                        }
                        //Transaction Fail someone else is using the withdraw function
                        player.sendMessage("This network is being withdrawn from by " + ChatColor.YELLOW + user);
                        player.sendMessage(ChatColor.GOLD + "Please wait...");
                        event.setCancelled(true);
                        return;
                    }
                    else {
                        CustomPlayer settings = CustomPlayer.getSettings(event.getPlayer());
                        settings.clearPlayer();
                        NetworkItem netItem = network.withdrawChests.get(block);
                        if (netItem == null) netItem = network.withdrawChests.get(plugin.util.doubleChest(block));
                        if (netItem == null) return;
                        Block signBlock = netItem.sign;
                        if (signBlock.getType().equals(Material.WALL_SIGN) && signBlock.getLocation().getBlock().getType().equals(Material.WALL_SIGN)) {
                            Sign chestSign = (Sign) signBlock.getState();
                            for(int line = 0; line < 4; line++)
                                chestSign.setLine(line, "");
                            chestSign.update();
                        }
                        plugin.allNetworkBlocks.remove(block);
                        plugin.allNetworkBlocks.remove(plugin.util.doubleChest(block));
                        plugin.allNetworkBlocks.remove(signBlock);
                        network.withdrawChests.remove(block);
                        event.getPlayer().sendMessage(ChatColor.BLUE + "Withdraw chest removed.");
                        return;
                    }
                }
                else {
                    SortChest sortChest = network.findSortChest(block);
                    if (sortChest == null) sortChest = network.findSortChest(plugin.util.doubleChest(block));
                    if (sortChest == null) return;
                    if (sortChest.sign.getType().equals(Material.WALL_SIGN) && sortChest.sign.getLocation().getBlock().getType().equals(Material.WALL_SIGN)) {
                        Sign chestSign = (Sign) sortChest.sign.getState();
                        for(int line = 0; line < 4; line++)
                            chestSign.setLine(line, "");
                        chestSign.update();
                    }
                    plugin.allNetworkBlocks.remove(block);
                    plugin.allNetworkBlocks.remove(plugin.util.doubleChest(block));
                    plugin.allNetworkBlocks.remove(sortChest.sign);
                    network.sortChests.remove(sortChest);
                    event.getPlayer().sendMessage(ChatColor.BLUE + "Sort chest removed.");
                    return;
                }
            }
            else {
                event.getPlayer().sendMessage("This network is owned by " + ChatColor.YELLOW + network.owner);
                event.getPlayer().sendMessage(ChatColor.RED + "You can't modify this network.");
                event.setCancelled(true);
                return;
            }
        }
    }

    //TODO Helper Methods / Classes

    private boolean lavaFurnaceCheck(Player player, SortNetwork sortNetwork, Block storageBlock, String part) {
        if (!part.toLowerCase().contains("lavafurnace")) return true;
        LavaFurnace lfp = (LavaFurnace) LavaFurnace.plugin;
        FurnaceObject fo = lfp.furnaceHelper.findFurnaceFromProductionChest(sortNetwork.owner, storageBlock);
        if (fo == null) {
            fo = lfp.furnaceHelper.findFurnaceFromProductionChest(sortNetwork.owner, plugin.util.doubleChest(storageBlock));
        }
        if (fo == null) { return true; }
        if (fo.creator.equals(player.getName()) || plugin.playerHasPermission(player, "autosort.override")) return true;
        return false;
    }

    private boolean hopperDropperStopper(List<Block> blocksToTest, Player player) {
        String owner = player.getName();
        for(Block testBlock : blocksToTest) {
            if (!plugin.util.isValidInventoryBlock(testBlock)) continue;
            SortNetwork sortNet = plugin.allNetworkBlocks.get(testBlock);
            if (sortNet != null) {
                if (!owner.equals(sortNet.owner)) {
                    player.sendMessage("This network is owned by " + ChatColor.YELLOW + sortNet.owner);
                    player.sendMessage(ChatColor.RED + "You can not access or modify this Network.");
                    return true;
                }
            }
        }
        return false;
    }

    private boolean doubleChestPlaceChest(Material mat, Block block, Player player) {
        Block blockToTest = plugin.util.doubleChest(block);
        if (blockToTest.getType().equals(mat)) {
            SortNetwork net = plugin.allNetworkBlocks.get(blockToTest);
            if (net == null) return false;

            if (!net.owner.equals(player.getName())) {
                player.sendMessage("This network is owned by " + ChatColor.YELLOW + net.owner);
                player.sendMessage(ChatColor.RED + "You can not access or modify this Network.");
                return true;
            }
            else if (chestLock.containsValue(net)) {
                String user = "";
                for(Entry<String, SortNetwork> sortNet : chestLock.entrySet()) {
                    if (sortNet.getValue().equals(net)) {
                        user = sortNet.getKey();
                        break;
                    }
                }
                //Transaction Fail someone else is using the withdraw function
                player.sendMessage("This network is being withdrawn from by " + ChatColor.YELLOW + user);
                player.sendMessage(ChatColor.GOLD + "Please wait...");
                return true;
            }
            else if (net.owner.equals(player.getName()))
                    plugin.allNetworkBlocks.put(block, net);
        }
        return false;
    }

    private List<Block> getBlocksToTest(int blockData, Block block) {
        List<Block> blocksToTest = new ArrayList<Block>();
        switch (blockData) {
            case 0:
                blocksToTest.add(block.getRelative(BlockFace.UP));
                blocksToTest.add(block.getRelative(BlockFace.DOWN));
                break;
            case 2:
                blocksToTest.add(block.getRelative(BlockFace.UP));
                blocksToTest.add(block.getRelative(BlockFace.NORTH));
                break;
            case 3:
                blocksToTest.add(block.getRelative(BlockFace.UP));
                blocksToTest.add(block.getRelative(BlockFace.SOUTH));
                break;
            case 4:
                blocksToTest.add(block.getRelative(BlockFace.UP));
                blocksToTest.add(block.getRelative(BlockFace.WEST));
                break;
            case 5:
                blocksToTest.add(block.getRelative(BlockFace.UP));
                blocksToTest.add(block.getRelative(BlockFace.EAST));
                break;
            default:
                blocksToTest.add(block.getRelative(BlockFace.UP));
                break;
        }
        return blocksToTest;
    }

    private Block findHopper(Block block) {
        BlockFace[] surchest = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN };
        for(BlockFace face : surchest) {
            Block otherHalf = block.getRelative(face);
            if (otherHalf.getType().equals(Material.HOPPER)) { return otherHalf; }
        }
        return block;
    }

    private boolean isValidSign(Block block) {
        return block.getType().equals(Material.WALL_SIGN) || block.getType().equals(Material.SIGN_POST);
    }

    private Block getDirection(String dStr, Block signBlock) {
        Location loc = signBlock.getLocation();
        int signData = signBlock.getData();
        int x = 0;
        int y = 0;
        int z = 0;
        World world = loc.getWorld();
        String dir;
        for(int i = 0; i < dStr.length(); i++) {
            dir = new Character(dStr.charAt(i)).toString();
            if (dir.equalsIgnoreCase("L")) {
                switch (signData) {
                    case 2:
                        x++;
                        break;
                    case 3:
                        x--;
                        break;
                    case 4:
                        z--;
                        break;
                    case 5:
                        z++;
                        break;
                }
            }
            if (dir.equalsIgnoreCase("R")) {
                switch (signData) {
                    case 2:
                        x--;
                        break;
                    case 3:
                        x++;
                        break;
                    case 4:
                        z++;
                        break;
                    case 5:
                        z--;
                        break;
                }
            }
            if (dir.equalsIgnoreCase("U")) {
                y++;
            }
            if (dir.equalsIgnoreCase("D")) {
                y--;
            }
            if (dir.equalsIgnoreCase("N")) {
                z--;
            }
            if (dir.equalsIgnoreCase("E")) {
                x++;
            }
            if (dir.equalsIgnoreCase("S")) {
                z++;
            }
            if (dir.equalsIgnoreCase("W")) {
                x--;
            }
        }

        if (x == 0 && y == 0 && z == 0) y--;
        int newX = loc.getBlockX() + x;
        int newY = loc.getBlockY() + y;
        int newZ = loc.getBlockZ() + z;
        return new Location(world, newX, newY, newZ).getBlock();
    }

    private boolean isValid(String str) {
        if (str != null) {
            if (str.contains(":")) {
                String[] parts = str.split(":");
                if (parts.length == 2) {
                    String sid = parts[0];
                    String sdam = parts[1];
                    if (Util.isNumeric(sid) && Util.isNumeric(sdam)) {
                        int id = Integer.parseInt(sid);
                        Material mat = Material.getMaterial(id);
                        if (mat != null) { return true; }
                    }
                }
            }
            else if (Util.isNumeric(str)) {
                Material mat = Material.getMaterial(Integer.parseInt(str));
                if (mat != null) { return true; }
            }
            else if (str.equalsIgnoreCase("MISC")) {
                return true;
            }
            else if (AutoSort.customMatGroups.containsKey(str)) {
                return true;
            }
            else {
                Material mat = Material.getMaterial(str);
                if (mat != null) { return true; }
            }
        }
        return false;
    }

    private int getPriority(String pStr) {
        if (Util.isNumeric(pStr)) {
            int pInt = Integer.parseInt(pStr);
            if (pInt > 0 && pInt < 5) { return pInt; }
        }
        return -1;
    }

    // Create a new network
    private SortNetwork createNetwork(Player player, String netName) {
        String owner = player.getName();
        SortNetwork newNet = new SortNetwork(owner, netName, player.getWorld().getName());
        if (plugin.networks.containsKey(owner)) {
            plugin.networks.get(owner).add(newNet);
        }
        else {
            ArrayList<SortNetwork> networks = new ArrayList<SortNetwork>();
            networks.add(newNet);
            plugin.networks.put(owner, networks);
        }
        player.sendMessage(ChatColor.BLUE + "New network " + ChatColor.GRAY + netName + ChatColor.BLUE + " by " + ChatColor.GRAY + owner + ChatColor.BLUE + " created in " + ChatColor.GRAY + newNet.world + ChatColor.BLUE + ".");
        return newNet;
    }

    private boolean isInNetwork(Player player, Block storageBlock) {
        if (plugin.allNetworkBlocks.containsKey(storageBlock)) {
            player.sendMessage(ChatColor.RED + "You can't add this chest, it is already in a network!");
            return true;
        }
        else
            return false;
    }

    private int getProximity(String netName) {
        if (AutoSort.proximities.containsKey(netName)) {
            return AutoSort.proximities.get(netName);
        }
        else {
            return AutoSort.defaultProx;
        }

    }

    private Location getOrigin(List<SortChest> sortChests) {
        return sortChests.size() > 0 ? sortChests.get(0).block.getLocation() : null;
    }
}
