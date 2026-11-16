package ru.voidmain.enchants;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;

import java.util.*;

public final class VoidEnchantments extends JavaPlugin implements Listener, CommandExecutor {
    private NamespacedKey enchantKey;
    private NamespacedKey levelKey;
    private final Set<UUID> drilling = new HashSet<>();
    private final Map<UUID, Integer> hitCounters = new HashMap<>();
    private final Map<UUID, Long> hoverTicks = new HashMap<>();

    private record Spec(String key, String name, String lore, Material icon, int max, boolean leveled) {}
    private final List<Spec> specs = List.of(
        new Spec("magnet","Магнит","Добытые предметы притягиваются прямо в инвентарь",Material.IRON_INGOT,1,false),
        new Spec("drill","Бур","Разрушает область 3×3×3",Material.DIAMOND_PICKAXE,1,false),
        new Spec("autosmelt","Автоплавка","Автоматически переплавляет руду",Material.FURNACE,1,false),
        new Spec("veinminer","Цепная добыча","Добывает связанную рудную жилу",Material.RAW_IRON,1,false),
        new Spec("haste","Скорокоп","Ускорение добычи",Material.GOLDEN_PICKAXE,3,true),
        new Spec("experience","Опытный","Увеличивает опыт с руды",Material.EXPERIENCE_BOTTLE,4,true),
        new Spec("vampirism","Вампиризм","Каждый пятый удар восстанавливает здоровье",Material.REDSTONE,2,true),
        new Spec("frost","Ледяной клинок","Каждый третий удар замедляет цель",Material.PACKED_ICE,1,false),
        new Spec("venom","Веном","Каждый третий удар отравляет цель",Material.SPIDER_EYE,1,false),
        new Spec("execution","Казнь","Урон ×2 по цели с менее 30% здоровья",Material.IRON_SWORD,1,false),
        new Spec("thunder","Громовой удар","Шанс молнии при ударе",Material.TRIDENT,3,true),
        new Spec("dragonbreath","Дыхание дракона","Шанс тошноты у атакующего",Material.DRAGON_BREATH,1,false),
        new Spec("sneak","Скороход","Сохраняет скорость при движении крадучись",Material.LEATHER_LEGGINGS,1,false),
        new Spec("diver","Водолаз","Увеличивает скорость плавания",Material.HEART_OF_THE_SEA,3,true),
        new Spec("immunity","Иммунитет","Шанс снять отрицательные эффекты",Material.TOTEM_OF_UNDYING,1,false),
        new Spec("softsteps","Мягкие лапки","Снижает урон от падения",Material.FEATHER,3,true),
        new Spec("lavawalker","Лавоход","Превращает лаву под ногами в обсидиан",Material.OBSIDIAN,1,false),
        new Spec("owl","Сова","Ночное зрение",Material.SPYGLASS,1,false),
        new Spec("sniper","Снайпер","Усиливает дальний бой из лука",Material.TARGET,3,true),
        new Spec("wisdom","Мудрость","Увеличивает получаемый опыт",Material.BOOK,1,false),
        new Spec("wings","Крылья","Прыжок в элитрах восстанавливает 30% прочности",Material.ELYTRA,1,false),
        new Spec("hover","Парение","Shift позволяет зависать в воздухе",Material.PHANTOM_MEMBRANE,1,false),
        new Spec("windspeed","Скорость ветра","Удваивает скорость полёта, расходуя прочность ×2",Material.FIREWORK_ROCKET,1,false)
    );
    private final Set<String> singleTier = new HashSet<>();

    @Override public void onEnable() {
        enchantKey = new NamespacedKey(this,"custom_enchant");
        levelKey = new NamespacedKey(this,"custom_level");
        for (Spec s : specs) if (!s.leveled()) singleTier.add(s.key());
        getServer().getPluginManager().registerEvents(this,this);
        Objects.requireNonNull(getCommand("voidenchants")).setExecutor(this);
        getLogger().info("VoidEnchantments enabled: "+specs.size()+" enchantments");
    }

    private Spec spec(String key) { return specs.stream().filter(s -> s.key().equalsIgnoreCase(key)).findFirst().orElse(null); }
    private String id(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(enchantKey,PersistentDataType.STRING);
    }
    private int level(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 1;
        Integer n = item.getItemMeta().getPersistentDataContainer().get(levelKey,PersistentDataType.INTEGER);
        return n == null ? 1 : n;
    }
    private ItemStack book(Spec s,int lvl) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName("§x§9§B§3§0§F§F§l"+s.name());
        m.setLore(List.of("§7"+s.lore(),"","§dРедкость: §f"+(s.leveled()?"Улучшенное":"Особое"),"§7Уровень: §f"+(s.leveled()?roman(lvl):"I"),"","§8VoidMain Enchantments"));
        m.getPersistentDataContainer().set(enchantKey,PersistentDataType.STRING,s.key());
        m.getPersistentDataContainer().set(levelKey,PersistentDataType.INTEGER,lvl);
        item.setItemMeta(m);
        return item;
    }
    private String roman(int n) { return switch(n){case 1->"I";case 2->"II";case 3->"III";case 4->"IV";default->String.valueOf(n);}; }
    private ItemStack held(Player p) { return p.getInventory().getItemInMainHand(); }
    private boolean has(Player p,String key) {
        for (ItemStack it : p.getInventory().getArmorContents()) if (key.equals(id(it))) return true;
        return key.equals(id(held(p))) || key.equals(id(p.getInventory().getItemInOffHand()));
    }
    private int equippedLevel(Player p,String key) {
        ItemStack[] all = new ItemStack[]{held(p),p.getInventory().getItemInOffHand(),p.getInventory().getHelmet(),p.getInventory().getChestplate(),p.getInventory().getLeggings(),p.getInventory().getBoots()};
        for(ItemStack it:all) if(key.equals(id(it))) return level(it);
        return 0;
    }

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(args.length>0 && args[0].equalsIgnoreCase("give")) {
            if(!sender.hasPermission("voidenchants.admin")) { sender.sendMessage("§cНет прав."); return true; }
            if(args.length<3) { sender.sendMessage("§c/ve give <игрок> <key> [level]"); return true; }
            Player target=Bukkit.getPlayerExact(args[1]); Spec s=spec(args[2]);
            if(target==null || s==null) { sender.sendMessage("§cИгрок или зачарование не найдено."); return true; }
            int lvl=1; if(s.leveled() && args.length>3) try{lvl=Math.max(1,Math.min(s.max(),Integer.parseInt(args[3])));}catch(Exception ignored){}
            target.getInventory().addItem(book(s,lvl)).values().forEach(it->target.getWorld().dropItemNaturally(target.getLocation(),it));
            sender.sendMessage("§dВыдано: §f"+s.name()+" "+roman(lvl)); return true;
        }
        if(sender instanceof Player p) openCatalog(p,0); else sender.sendMessage("Команда доступна игроку.");
        return true;
    }
    private void openCatalog(Player p,int page) {
        Inventory inv=Bukkit.createInventory(null,54,"§0✦ §5VoidMain §8| §dЗачарования");
        int start=page*45;
        for(int i=0;i<45 && start+i<specs.size();i++) {
            Spec s=specs.get(start+i);
            ItemStack icon=new ItemStack(s.icon()); ItemMeta m=icon.getItemMeta();
            m.setDisplayName("§d§l"+s.name());
            m.setLore(List.of("§7"+s.lore(),"","§7Тип: §f"+(s.leveled()?"I–"+roman(s.max()):"Без уровней"),"§8Нажми, чтобы получить книгу"));
            m.getPersistentDataContainer().set(enchantKey,PersistentDataType.STRING,s.key());
            icon.setItemMeta(m); inv.setItem(i,icon);
        }
        ItemStack close=new ItemStack(Material.BARRIER); ItemMeta cm=close.getItemMeta(); cm.setDisplayName("§cЗакрыть"); close.setItemMeta(cm); inv.setItem(49,close);
        p.openInventory(inv);
    }
    @EventHandler public void onCatalog(InventoryClickEvent e) {
        if(!e.getView().getTitle().contains("VoidMain") || !(e.getWhoClicked() instanceof Player p)) return;
        e.setCancelled(true);
        ItemStack clicked=e.getCurrentItem(); String key=id(clicked); if(key==null) { if(clicked!=null && clicked.getType()==Material.BARRIER)p.closeInventory(); return; }
        Spec s=spec(key); if(s!=null) { p.getInventory().addItem(book(s,1)).values().forEach(it->p.getWorld().dropItemNaturally(p.getLocation(),it)); p.playSound(p.getLocation(),Sound.UI_BUTTON_CLICK,0.7f,1.5f); }
    }

    @EventHandler public void protectAnvil(PrepareAnvilEvent e) {
        ItemStack a=e.getInventory().getItem(0), b=e.getInventory().getItem(1);
        String ka=id(a), kb=id(b);
        if((ka!=null && singleTier.contains(ka)) || (kb!=null && singleTier.contains(kb))) e.setResult(null);
    }
    @EventHandler public void anvilClick(InventoryClickEvent e) {
        if(!(e.getInventory() instanceof AnvilInventory)) return;
        if(e.getRawSlot()==2) {
            String a=id(e.getInventory().getItem(0)), b=id(e.getInventory().getItem(1));
            if((a!=null && singleTier.contains(a)) || (b!=null && singleTier.contains(b))) {
                e.setCancelled(true);
                if(e.getWhoClicked() instanceof Player p) p.sendMessage("§cЭто зачарование нельзя объединять в наковальне. Книги сохранены.");
            }
        }
    }

    @EventHandler public void onMine(BlockBreakEvent e) {
        Player p=e.getPlayer(); ItemStack tool=held(p); String ench=id(tool);
        if(ench==null) return;
        if(ench.equals("haste")) p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,100,level(tool)-1,true,false));
        if(ench.equals("magnet")) Bukkit.getScheduler().runTask(this,()->{
            for(Entity ent:p.getNearbyEntities(6,6,6)) if(ent instanceof Item drop) {
                ItemStack stack=drop.getItemStack().clone();
                Map<Integer,ItemStack> rest=p.getInventory().addItem(stack);
                if(rest.isEmpty()) drop.remove(); else drop.setItemStack(rest.values().iterator().next());
            }
        });
        if(ench.equals("autosmelt")) {
            Collection<ItemStack> drops=e.getBlock().getDrops(tool,p);
            List<ItemStack> cooked=new ArrayList<>();
            for(ItemStack d:drops) {
                Recipe recipe=Bukkit.getRecipesFor(new ItemStack(d.getType())).stream().filter(r->r instanceof FurnaceRecipe || r instanceof BlastingRecipe).findFirst().orElse(null);
                if(recipe instanceof CookingRecipe<?> cr) cooked.add(cr.getResult().asQuantity(d.getAmount())); else cooked.add(d);
            }
            e.setDropItems(false); Bukkit.getScheduler().runTask(this,()->cooked.forEach(d->p.getWorld().dropItemNaturally(e.getBlock().getLocation(),d)));
        }
        if(ench.equals("drill") && drilling.add(p.getUniqueId())) {
            Block center=e.getBlock(); World w=center.getWorld();
            Bukkit.getScheduler().runTask(this,()->{
                try {
                    for(int x=-1;x<=1;x++) for(int y=-1;y<=1;y++) for(int z=-1;z<=1;z++) {
                        if(x==0&&y==0&&z==0) continue;
                        Block b=w.getBlockAt(center.getX()+x,center.getY()+y,center.getZ()+z);
                        if(b.getType().isAir() || b.getType().getHardness()<0) continue;
                        b.breakNaturally(tool);
                    }
                } finally { drilling.remove(p.getUniqueId()); }
            });
        }
        if(ench.equals("veinminer")) {
            Material type=e.getBlock().getType();
            if(type.name().endsWith("_ORE") || type==Material.ANCIENT_DEBRIS) {
                Location origin=e.getBlock().getLocation();
                Bukkit.getScheduler().runTask(this,()->{
                    Queue<Block> q=new ArrayDeque<>(); Set<Block> seen=new HashSet<>(); q.add(origin.getBlock()); seen.add(origin.getBlock());
                    int count=0;
                    while(!q.isEmpty() && count<64) { Block b=q.poll(); if(b.getType()==type && b.getLocation().distanceSquared(origin)<=100) { if(b!=origin.getBlock()){b.breakNaturally(tool);count++;} for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){Block n=b.getRelative(x,y,z);if(seen.add(n))q.add(n);} } }
                });
            }
        }
    }

    @EventHandler public void onHit(EntityDamageByEntityEvent e) {
        if(!(e.getDamager() instanceof Player p)) return;
        ItemStack weapon=held(p); String k=id(weapon); if(k==null)return;
        int lvl=level(weapon); UUID uid=p.getUniqueId();
        if(k.equals("execution") && e.getEntity() instanceof LivingEntity le && le.getHealth()<=le.getMaxHealth()*0.30) e.setDamage(e.getDamage()*2);
        if(k.equals("vampirism") && hitCounters.merge(uid,1,Integer::sum)%5==0) p.setHealth(Math.min(p.getMaxHealth(),p.getHealth()+lvl));
        if(e.getEntity() instanceof LivingEntity target) {
            if(k.equals("frost") && hitCounters.merge(uid,1,Integer::sum)%3==0) target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,40,0));
            if(k.equals("venom") && hitCounters.merge(uid,1,Integer::sum)%3==0) target.addPotionEffect(new PotionEffect(PotionEffectType.POISON,100,0));
            if(k.equals("thunder") && Math.random()<0.10*lvl) target.getWorld().strikeLightningEffect(target.getLocation());
        }
    }
    @EventHandler public void onDamage(EntityDamageEvent e) {
        if(!(e.getEntity() instanceof Player p)) return;
        if(has(p,"immunity") && Math.random()<0.20) for(PotionEffect effect:new ArrayList<>(p.getActivePotionEffects())) if(effect.getType().equals(PotionEffectType.SLOWNESS)||effect.getType().equals(PotionEffectType.WEAKNESS)||effect.getType().equals(PotionEffectType.BLINDNESS)||effect.getType().equals(PotionEffectType.POISON)||effect.getType().equals(PotionEffectType.NAUSEA)) p.removePotionEffect(effect.getType());
        int soft=equippedLevel(p,"softsteps"); if(e.getCause()==EntityDamageEvent.DamageCause.FALL && soft>0) e.setDamage(e.getDamage()*(1-0.30*soft));
        if(e.getCause()==EntityDamageEvent.DamageCause.FALL && soft>=3)e.setDamage(0);
        if(has(p,"dragonbreath") && Math.random()<0.10 && e instanceof EntityDamageEvent) { }
    }
    @EventHandler public void attackerNausea(EntityDamageByEntityEvent e) {
        if(e.getEntity() instanceof Player victim && e.getDamager() instanceof LivingEntity attacker && has(victim,"dragonbreath") && Math.random()<0.10)
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA,100,0));
    }
    @EventHandler public void onMove(PlayerMoveEvent e) {
        Player p=e.getPlayer();
        if(has(p,"owl") && !p.hasPotionEffect(PotionEffectType.NIGHT_VISION)) p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,260,0,true,false));
        if(has(p,"lavawalker")) {
            Block under=p.getLocation().subtract(0,1,0).getBlock();
            if(under.getType()==Material.LAVA) for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){Block b=under.getRelative(x,0,z);if(b.getType()==Material.LAVA)b.setType(Material.OBSIDIAN);}
        }
        if(p.isSneaking() && has(p,"sneak") && p.isSprinting()) p.setWalkSpeed(0.2f);
        if(has(p,"hover") && p.isSneaking() && !p.isOnGround()) { p.setVelocity(new Vector(p.getVelocity().getX(),0,p.getVelocity().getZ())); p.setFallDistance(0); }
    }
    @EventHandler public void onJump(PlayerJumpEvent e) {
        Player p=e.getPlayer();
        if(has(p,"wings") && p.isGliding() && p.getInventory().getChestplate()!=null) {
            ItemStack chest=p.getInventory().getChestplate(); if(chest.getType()==Material.ELYTRA && chest.getItemMeta() instanceof Damageable d) {
                int restore=(int)Math.ceil(chest.getType().getMaxDurability()*0.30);
                d.setDamage(Math.max(0,d.getDamage()-restore)); chest.setItemMeta(d); p.sendMessage("§d✦ §fКрылья восстановили 30% прочности элитр!");
            }
        }
    }
    @EventHandler public void onConsume(PlayerItemConsumeEvent e) {
        Player p=e.getPlayer(); if(has(p,"wisdom")) p.giveExp(1);
    }
    @EventHandler public void onExp(PlayerExpChangeEvent e) {
        Player p=e.getPlayer(); int lv=equippedLevel(p,"experience"); if(lv>0)e.setAmount(e.getAmount()*(1+lv));
        if(has(p,"wisdom"))e.setAmount(e.getAmount()*2);
    }
    @EventHandler public void onToggleGlide(EntityToggleGlideEvent e) {
        if(e.getEntity() instanceof Player p && e.isGliding() && has(p,"windspeed")) p.setVelocity(p.getVelocity().multiply(2));
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) { hitCounters.remove(e.getPlayer().getUniqueId()); }
}
