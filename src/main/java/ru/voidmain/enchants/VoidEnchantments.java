package ru.voidmain.enchants;

import org.bukkit.*;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.LootGenerateEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;

import java.util.*;

public final class VoidEnchantments extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private NamespacedKey enchantKey;
    private NamespacedKey levelKey;
    private NamespacedKey categoryKey;
    private NamespacedKey pageKey;
    private final Set<UUID> drilling = new HashSet<>();
    private final Map<UUID, Integer> hitCounters = new HashMap<>();
    private final Map<UUID, Long> hoverTicks = new HashMap<>();
    private final Map<UUID, Integer> radarTasks = new HashMap<>();
    private final Map<UUID, Map<Integer, Entity>> radarTargets = new HashMap<>();
    private final Map<String, Integer> maceHitCounters = new HashMap<>();
    private final Map<UUID, Double> sneakSpeeds = new HashMap<>(), berserkBase = new HashMap<>();
    private final Map<UUID, Long> doubleJumpCooldown = new HashMap<>(), shadowSince = new HashMap<>();
    private final Map<String,Long> procCooldowns=new HashMap<>();
    private final Map<String,Long> procCooldownDurations=new HashMap<>();
    private final Map<UUID, Deque<String>> activationHistory = new HashMap<>();
    private final Map<UUID, Boolean> effectsEnabled = new HashMap<>();
    private final Map<UUID, Map<String,Integer>> awakeningCounts = new HashMap<>();
    private final Set<String> cooldownHudKeys=Set.of("hook","bounce","doublejump","immortality","titanshield","bloodsucker","shove","lightaura","secondwind");
    private final Map<UUID,Integer> shieldAxeHits=new HashMap<>();
    private final Set<UUID> treeBreaking=new HashSet<>();
    private final Map<UUID, Location> shadowPositions = new HashMap<>();
    private final Map<UUID, Integer> shadowTasks = new HashMap<>();
    private final Set<Location> lavaBlocks = new HashSet<>();
    private final Set<UUID> wingsJumping = new HashSet<>();
    private final Map<String, Integer> villagerBookDrops = new HashMap<>();
    private static final String ADMIN_TITLE = "§0✦ §5VoidMain §8| §cАдмин-панель";
    private static final String CATALOG_TITLE = "§0✦ §5VoidMain §8| §dЗачарования";
    private static final String LEVEL_TITLE = "§0✦ §5VoidMain §8| §dВыбор уровня";
    private static final String CATEGORY_TITLE = "§0✦ §5VoidMain §8| §dВыбор предмета";

    private record Spec(String key, String name, String lore, Material icon, int max, boolean leveled) {}
    private record Category(String key, String title, Material icon) {}
    private final List<Category> categories = List.of(
        new Category("swords","МЕЧИ",Material.NETHERITE_SWORD),
        new Category("axes","ТОПОРЫ",Material.NETHERITE_AXE),
        new Category("mace","БУЛАВА",Material.MACE),
        new Category("pickaxes","КИРКИ",Material.NETHERITE_PICKAXE),
        new Category("armor","БРОНЯ",Material.DIAMOND_CHESTPLATE),
        new Category("shields","ЩИТЫ",Material.SHIELD),
        new Category("bows","ЛУКИ",Material.BOW),
        new Category("spears","КОПЬЯ",Material.TRIDENT),
        new Category("elytra","ЭЛИТРЫ",Material.ELYTRA)
    );
    private final List<Spec> specs = List.of(
        new Spec("magnet","Магнит","Добытые предметы притягиваются прямо в инвентарь",Material.IRON_INGOT,1,false),
        new Spec("woodcutter","Дровосек","Ломает связанные брёвна дерева",Material.IRON_AXE,1,false),
        new Spec("cleave","Раскол","Шанс 15% наложить Слабость на 5 секунд",Material.IRON_AXE,1,false),
        new Spec("stun","Оглушение","Каждый третий удар оглушает на 1,5 секунды",Material.ANVIL,1,false),
        new Spec("heaviness","Тяжесть","Каждый второй удар замедляет на 1 секунду",Material.ANVIL,1,false),
        new Spec("axemaster","Мастер топора","Игнорирует броню: I 5%, II 10%, III 15%",Material.NETHERITE_AXE,3,true),
        new Spec("fish","Рыба","Позволяет дышать под водой",Material.PUFFERFISH,1,false),
        new Spec("drill","Бур","Разрушает область 3×3×3",Material.DIAMOND_PICKAXE,1,false),
        new Spec("autosmelt","Автоплавка","Автоматически переплавляет руду",Material.FURNACE,1,false),
        new Spec("veinminer","Цепная добыча","Добывает связанную рудную жилу",Material.RAW_IRON,1,false),
        new Spec("haste","Скорокоп","Ускорение добычи",Material.GOLDEN_PICKAXE,3,true),
        new Spec("experience","Опытный","Увеличивает опыт с руды",Material.EXPERIENCE_BOTTLE,4,true),
        new Spec("vampirism","Вампиризм","Каждый пятый удар восстанавливает здоровье",Material.REDSTONE,2,true),
        new Spec("frost","Ледяной клинок","Каждый третий удар замедляет цель",Material.PACKED_ICE,1,false),
        new Spec("venom","Веном","Каждый третий удар отравляет цель",Material.SPIDER_EYE,1,false),
        new Spec("execution","Казнь","Урон ×2 по цели с менее 30% здоровья",Material.IRON_SWORD,1,false),
        new Spec("thunder","Тор","Улучшенная молния с шансом 20%",Material.MACE,1,false),
        new Spec("dragonbreath","Дыхание дракона","Шанс тошноты у атакующего",Material.DRAGON_BREATH,1,false),
        new Spec("sneak","Скороход","При Shift сохраняет обычную скорость ходьбы и бега",Material.LEATHER_LEGGINGS,1,false),
        new Spec("diver","Водолаз","Увеличивает скорость плавания",Material.HEART_OF_THE_SEA,3,true),
        new Spec("immunity","Иммунитет","Шанс снять отрицательные эффекты",Material.TOTEM_OF_UNDYING,1,false),
        new Spec("softsteps","Мягкие лапки","Снижает урон от падения",Material.FEATHER,3,true),
        new Spec("lavawalker","Лавоход","Создаёт обсидиан под ногами на 4 секунды",Material.OBSIDIAN,1,false),
        new Spec("owl","Сова","Ночное зрение",Material.SPYGLASS,1,false),
        new Spec("sniper","Снайпер","Усиливает дальний бой из лука",Material.TARGET,3,true),
        new Spec("wisdom","Мудрость","Увеличивает получаемый опыт",Material.BOOK,1,false),
        new Spec("wings","Крылья","Прыжок в элитрах восстанавливает 30% прочности",Material.ELYTRA,1,false),
        new Spec("hover","Парение","Shift позволяет зависать в воздухе",Material.PHANTOM_MEMBRANE,1,false),
        new Spec("blackhole","Чёрная дыра","Каждый второй удар булавой обездвиживает цель на 2 секунды",Material.ENDER_EYE,1,false),
        new Spec("phantomblade","Призрачный клинок","После удара становишься невидимым на 5 секунд",Material.IRON_SWORD,1,false),
        new Spec("radar","Радар","Отмечает игроков и мобов в радиусе 20 блоков визуальными метками",Material.COMPASS,1,false),
        new Spec("aerodynamics","Аэродинамика","Убирает урон от столкновения с блоками при полёте",Material.ELYTRA,1,false),
        new Spec("spear_dash","Рыцарь","Рывок копьём не расходует голод",Material.TRIDENT,1,false),
        new Spec("shadow","Тень","Если стоять в приседе 5 секунд — невидимость до движения",Material.BLACK_DYE,1,false),
        new Spec("doublejump","Двойной прыжок","Второй прыжок в воздухе; перезарядка 40 секунд",Material.RABBIT_FOOT,1,false),
        new Spec("hook","Крюк","ПКМ притягивает врага в радиусе 5 блоков и отравляет на 5 секунд",Material.LEAD,1,false),
        new Spec("armorbreaker","Сокрушитель брони","Игнорирует часть брони: I 10%, II 15%, III 25%",Material.NETHERITE_SWORD,3,true),
        new Spec("bounce","Отскок","ПКМ с булавой подбрасывает вверх примерно на 25 блоков",Material.MACE,1,false),
        new Spec("waterwalker","Водоход","Создаёт синий лёд под ногами на 3 секунды",Material.BLUE_ICE,1,false),
new Spec("moon","Луна","Ночью +30% урона, днём −30% урона",Material.CLOCK,1,false),

new Spec("berserk","Берсерк","Скорость атаки топором растёт при низком здоровье",Material.IRON_AXE,1,false),
new Spec("godslayer","Убийца Богов","Наносит двойной урон игрокам",Material.NETHERITE_SWORD,1,false),
new Spec("worldeater","Пожиратель Миров","Удар булавой с высоты: двойной урон и +2 сердца",Material.MACE,1,false),
new Spec("immortality","Бессмертие","Смертельный удар оставляет 1 сердце; защита 2 секунды",Material.TOTEM_OF_UNDYING,1,false),
new Spec("reflection","Отражение","Отражает 15% заблокированного урона",Material.SHIELD,1,false),
new Spec("secondwind","Второе дыхание","При блоке на низком здоровье даёт регенерацию II",Material.GHAST_TEAR,1,false),
new Spec("unbreakable","Не ломаемый","Щит выдерживает пять ударов топором",Material.SHIELD,1,false),
new Spec("lightaura","Аура света","Ослепляет, отравляет и ранит врагов при блоке",Material.GLOWSTONE,3,true),
new Spec("shove","Толчок","Отбрасывает атакующего при блоке",Material.PISTON,3,true),
new Spec("bloodsucker","Кровопийца","Восстанавливает здоровье при блоке",Material.REDSTONE,3,true),
new Spec("titanshield","Щит Титана","Блокирует удар щитом независимо от направления",Material.SHIELD,1,false)
    );
    private final Set<String> singleTier = new HashSet<>();

    @Override public void onEnable() {
        enchantKey = new NamespacedKey(this,"custom_enchant");
        levelKey = new NamespacedKey(this,"custom_level");
        categoryKey = new NamespacedKey(this,"catalog_category");
        pageKey = new NamespacedKey(this,"catalog_page");
        for (Spec s : specs) if (!s.leveled()) singleTier.add(s.key());
        getServer().getPluginManager().registerEvents(this,this);
        Objects.requireNonNull(getCommand("voidenchants")).setExecutor(this);
        Objects.requireNonNull(getCommand("voidenchants")).setTabCompleter(this);
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();
        Bukkit.getScheduler().runTaskTimer(this,()->Bukkit.getOnlinePlayers().forEach(p->{updateCooldownBar(p); refreshDiver(p); if(has(p,"berserk") && held(p).getType().name().endsWith("_AXE")) updateBerserk(p); else resetBerserk(p);}),5L,5L);
        saveConfig();
        getLogger().info("VoidEnchantments enabled: "+specs.size()+" enchantments");
    }

    private Spec spec(String key) { return specs.stream().filter(s -> s.key().equalsIgnoreCase(key)).findFirst().orElse(null); }
    private String specName(Spec s) { return getConfig().getString("enchantments."+s.key()+".name",s.name()); }
    private String specDescription(Spec s) { return getConfig().getString("enchantments."+s.key()+".description",s.lore()); }
    private int maxLevel(Spec s) { return Math.max(1,Math.min(10,getConfig().getInt("enchantments."+s.key()+".max-level",s.max()))); }
    private NamespacedKey itemEnchantKey(String key) { return new NamespacedKey(this, "ce_" + key); }
    private Set<String> enchantKeys(ItemStack item) {
        Set<String> keys=new LinkedHashSet<>();
        if(item==null || !item.hasItemMeta()) return keys;
        var pdc=item.getItemMeta().getPersistentDataContainer();
        String legacy=pdc.get(enchantKey,PersistentDataType.STRING);
        if(legacy!=null) keys.add(legacy);
        for(Spec spec:specs) if(pdc.has(itemEnchantKey(spec.key()),PersistentDataType.INTEGER) && isEnabled(spec.key())) keys.add(spec.key());
        return keys;
    }
    private String id(ItemStack item) {
        if(item==null || !item.hasItemMeta()) return null;
        String bookId=item.getItemMeta().getPersistentDataContainer().get(enchantKey,PersistentDataType.STRING);
        return bookId!=null?bookId:enchantKeys(item).stream().findFirst().orElse(null);
    }
    private boolean hasEnchant(ItemStack item,String key) {
        if(item==null || !item.hasItemMeta()) return false;
        var pdc=item.getItemMeta().getPersistentDataContainer();
        return key.equals(pdc.get(enchantKey,PersistentDataType.STRING)) || pdc.has(itemEnchantKey(key),PersistentDataType.INTEGER);
    }
    private int enchantLevel(ItemStack item,String key) {
        if(item==null || !item.hasItemMeta()) return 0;
        Integer value=item.getItemMeta().getPersistentDataContainer().get(itemEnchantKey(key),PersistentDataType.INTEGER);
        return value!=null?value:(key.equals(id(item))?level(item):0);
    }
    private int level(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 1;
        Integer n = item.getItemMeta().getPersistentDataContainer().get(levelKey,PersistentDataType.INTEGER);
        return n == null ? 1 : n;
    }
    private int rarityTier(Spec s) { int fallback = switch(s.key()) {
        case "godslayer","worldeater","immortality" -> 0;
        case "thunder","lightaura" -> 1;
        case "blackhole","spear_dash","stun" -> 1;
        case "shadow","hook","cleave","fish" -> 2;
        case "armorbreaker","axemaster","doublejump","woodcutter","heaviness" -> 3;
        case "bounce" -> 1;
        case "waterwalker" -> 4;
        case "unbreakable","titanshield" -> 1;
        case "reflection","shove" -> 3;
        case "secondwind","bloodsucker","berserk" -> 2;
        case "moon" -> 2;
        case "phantomblade","aerodynamics" -> 3;
        case "radar" -> 4;
        case "wings","execution" -> 1;
        case "drill","vampirism","sniper" -> 2;
        case "veinminer","hover","lavawalker","dragonbreath" -> 3;
        case "diver" -> 3;
        case "magnet","autosmelt","experience","frost","venom","immunity","softsteps" -> 4;
        default -> 5;
    }; return Math.max(0,Math.min(5,getConfig().getInt("enchantments."+s.key()+".rarity-tier",fallback))); }
    private String rarityName(int t) { return switch(t) {
        case 0 -> "АДМИНСКОЕ"; case 1 -> "Божественное"; case 2 -> "Легендарное"; case 3 -> "Эпическое"; case 4 -> "Редкое"; default -> "Необычное";
    }; }
    private String rarityColor(int t) { return switch(t) {
        case 0 -> "§c§l"; case 1 -> "§c"; case 2 -> "§6"; case 3 -> "§5"; case 4 -> "§b"; default -> "§a";
    }; }
    private String appliesTo(Spec s) { return switch(s.key()) {
        case "magnet" -> "Кирка, лопата или топор";
        case "moon","godslayer" -> "Меч";
        case "immortality" -> "Нагрудник";
        case "berserk" -> "Топор";
        case "worldeater" -> "Булава";
        case "reflection","secondwind","unbreakable","lightaura","shove","bloodsucker","titanshield" -> "Щит";
        case "woodcutter","cleave","stun","heaviness","axemaster" -> "Топор";
        case "fish" -> "Шлем";
        case "drill","autosmelt","veinminer","haste","experience" -> "Кирка";
        case "vampirism","frost","venom","execution","phantomblade" -> "Меч";
        case "armorbreaker" -> "Копьё";
        case "thunder","blackhole","bounce" -> "Булава";
        case "radar" -> "Шлем";
        case "aerodynamics" -> "Элитры";
        case "spear_dash","hook" -> "Копьё";
        case "dragonbreath" -> "Нагрудник";
        case "shadow","sneak","diver" -> "Поножи";
        case "doublejump","waterwalker","softsteps","lavawalker" -> "Ботинки";
        case "immunity" -> "Любая броня"; case "owl","wisdom" -> "Шлем";
        case "sniper" -> "Лук или арбалет"; case "wings","hover" -> "Элитры";
        default -> "Неизвестно";
    }; }
    private ItemStack book(Spec s,int lvl) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta m = item.getItemMeta();
        int tier=rarityTier(s);
        m.setDisplayName(gradient(specName(s),tier));
        m.setItemModel(NamespacedKey.fromString("voidmain:scroll_tier_"+tier));
        m.setLore(List.of("§7"+specDescription(s),"","§7Предмет: §f"+appliesTo(s),
            "§7Редкость: "+rarityColor(tier)+rarityName(tier),
            "§7Уровень: §f"+(s.leveled()?roman(lvl):"I"),"","§8VoidMain Enchantments"));
        m.getPersistentDataContainer().set(enchantKey,PersistentDataType.STRING,s.key());
        m.getPersistentDataContainer().set(levelKey,PersistentDataType.INTEGER,lvl);
        item.setItemMeta(m);
        return item;
    }
    private String roman(int n) { return switch(n){case 1->"I";case 2->"II";case 3->"III";case 4->"IV";default->String.valueOf(n);}; }
    private ItemStack held(Player p) { return p.getInventory().getItemInMainHand(); }
    private boolean has(Player p,String key) {
        for(ItemStack it:p.getInventory().getArmorContents()) if(hasEnchant(it,key)) return true;
        return hasEnchant(held(p),key)||hasEnchant(p.getInventory().getItemInOffHand(),key);
    }
    private int equippedLevel(Player p,String key) {
        ItemStack[] all = new ItemStack[]{held(p),p.getInventory().getItemInOffHand(),p.getInventory().getHelmet(),p.getInventory().getChestplate(),p.getInventory().getLeggings(),p.getInventory().getBoots()};
        for(ItemStack it:all) if(hasEnchant(it,key)) return enchantLevel(it,key);
        return 0;
    }

    private boolean isEnabled(String key) { return !getConfig().getStringList("disabled-enchants").contains(key); }
    private boolean adminOnly(String key) { Spec sp=spec(key); return sp!=null && rarityTier(sp)==0; }
    private long configuredCooldown(String key,long fallback) { return Math.max(0L,getConfig().getLong("cooldowns.seconds."+key, fallback/1000L))*1000L; }
    private boolean canCombine(ItemStack target,String key) {
        Set<String> existing=enchantKeys(target);
        if(existing.contains(key) || existing.size()>=getConfig().getInt("compatibility.max-enchants-per-item",2)) return false;
        for(String pair:getConfig().getStringList("compatibility.blocked-pairs")) {
            String[] bits=pair.toLowerCase(Locale.ROOT).split(":");
            if(bits.length==2 && ((bits[0].equals(key)&&existing.contains(bits[1]))||(bits[1].equals(key)&&existing.contains(bits[0])))) return false;
        }
        for(Map<?,?> group:getConfig().getMapList("compatibility.conflict-groups")) {
            Object raw=group.get("enchants");
            if(raw instanceof List<?> list && list.contains(key) && list.stream().anyMatch(v->existing.contains(String.valueOf(v)))) return false;
        }
        return true;
    }
    private long cooldownLeft(UUID uuid,String key) { return Math.max(0L,procCooldowns.getOrDefault(uuid+":"+key,0L)-System.currentTimeMillis()); }
    private void updateCooldownBar(Player p) {
        if(!getConfig().getBoolean("cooldowns.actionbar.enabled",true)) return;
        List<String> active=new ArrayList<>();
        for(Spec sp:specs) {
            if(!cooldownHudKeys.contains(sp.key())) continue;
            String id=p.getUniqueId()+":"+sp.key();
            long left=cooldownLeft(p.getUniqueId(),sp.key());
            if(left<=0 || !has(p,sp.key())) continue;
            long total=Math.max(left,procCooldownDurations.getOrDefault(id,left));
            int filled=Math.max(0,Math.min(10,(int)Math.round((1.0-(double)left/total)*10.0)));
            String bar="§d"+"▰".repeat(filled)+"§8"+"▱".repeat(10-filled);
            active.add("§5✦ "+gradient(specName(sp),rarityTier(sp))+" §8["+bar+"§8] §f"+String.format(Locale.US,"%.1f",left/1000.0)+"с");
        }
        int maxVisible=Math.max(1,Math.min(4,getConfig().getInt("cooldowns.actionbar.max-visible",2)));
        String output=active.isEmpty()?"":String.join(" §8◆ ",active.subList(0,Math.min(maxVisible,active.size())));
        if(active.size()>maxVisible) output+=" §8+"+(active.size()-maxVisible);
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(output));
    }
    private double defaultDropChance(Spec s) { return switch(rarityTier(s)) { case 1 -> 0.05; case 2 -> 0.15; case 3 -> 0.50; case 4 -> 1.50; default -> 4.00; }; }
    private double dropChance(Spec s) { double weight=Math.max(0,Math.min(100,getConfig().getDouble("drop-chance."+s.key(),defaultDropChance(s)))); return switch(rarityTier(s)) { case 1 -> weight/4.0; case 2 -> weight/2.5; case 3 -> weight/1.5; default -> weight; }; }
    private double totalDropWeight() { return specs.stream().filter(x->isEnabled(x.key()) && rarityTier(x)>0).mapToDouble(this::dropChance).sum(); }
    private String chanceText(Spec s) { if(rarityTier(s)==0) return "только админам"; double total=totalDropWeight(); double percent=total<=0?0:dropChance(s)/total*100.0; return String.format(Locale.US,"%.4f",percent).replaceAll("\\.?0+$","")+"%"; }
    private boolean ready(Player p,String key,long ms) {String k=p.getUniqueId()+":"+key;long now=System.currentTimeMillis();if(now<procCooldowns.getOrDefault(k,0L))return false;procCooldowns.put(k,now+ms);procCooldownDurations.put(k,Math.max(1L,ms));return true;}
    private void spawnVoidRing(Location center,double radius,Color color,int points) {
        World world=center.getWorld(); if(world==null)return;
        Particle.DustOptions dust=new Particle.DustOptions(color,1.15f);
        for(int i=0;i<points;i++) {
            double angle=Math.PI*2.0*i/points;
            world.spawnParticle(Particle.DUST,center.clone().add(Math.cos(angle)*radius,.12,Math.sin(angle)*radius),1,.01,.01,.01,0,dust);
        }
    }
    private void spawnBloodDrips(LivingEntity entity) {
        Particle.DustOptions blood=new Particle.DustOptions(Color.fromRGB(156,8,31),1.15f);
        new BukkitRunnable() {
            int frame=0;
            @Override public void run() {
                if(!entity.isValid() || frame>=10) { cancel(); return; }
                Location origin=entity.getLocation();
                for(int i=0;i<4;i++) {
                    double angle=frame*.62+i*Math.PI*2/4;
                    Location drop=origin.clone().add(Math.cos(angle)*.27,1.62-frame*.15,Math.sin(angle)*.27);
                    entity.getWorld().spawnParticle(Particle.DUST,drop,1,.006,.018,.006,0,blood);
                }
                entity.getWorld().spawnParticle(Particle.DUST,origin.clone().add(0,.18,0),2,.18,.02,.18,0,blood);
                frame++;
            }
        }.runTaskTimer(this,0L,1L);
    }
    private void playEnchantSound(Player player,String key,Location location) {
        if(location.getWorld()==null) return;
        String first="minecraft:block.amethyst_block.chime";
        String second=null;
        float volume=0.85f, pitch=1.0f;
        switch(key) {
            case "godslayer" -> { first="minecraft:block.beacon.activate"; second="minecraft:entity.elder_guardian.curse"; volume=1.15f; pitch=0.72f; }
            case "worldeater" -> { first="minecraft:entity.warden.sonic_boom"; second="minecraft:entity.ender_dragon.growl"; volume=1.35f; pitch=0.65f; }
            case "blackhole" -> { first="minecraft:block.end_portal.spawn"; second="minecraft:entity.enderman.teleport"; volume=0.95f; pitch=0.72f; }
            case "thunder" -> { first="minecraft:entity.lightning_bolt.impact"; second="minecraft:entity.lightning_bolt.thunder"; volume=1.0f; pitch=0.88f; }
            case "dragonbreath" -> { first="minecraft:entity.ender_dragon.flap"; second="minecraft:entity.blaze.shoot"; pitch=0.78f; }
            case "lightaura","immortality" -> { first="minecraft:block.beacon.power_select"; second="minecraft:item.totem.use"; volume=0.9f; pitch=1.08f; }
            case "moon" -> { first="minecraft:block.amethyst_block.chime"; second="minecraft:block.note_block.bell"; pitch=1.35f; }
            case "vampirism","bloodsucker" -> { first="minecraft:entity.warden.heartbeat"; second="minecraft:entity.player.attack.crit"; pitch=0.82f; }
            case "frost","waterwalker" -> { first="minecraft:block.glass.break"; second="minecraft:block.amethyst_block.chime"; pitch=1.28f; }
            case "venom" -> { first="minecraft:entity.spider.ambient"; second="minecraft:block.pointed_dripstone.drip_lava"; pitch=0.92f; }
            case "berserk" -> { first="minecraft:entity.ravager.roar"; second="minecraft:entity.player.attack.knockback"; pitch=0.82f; }
            case "execution" -> { first="minecraft:block.anvil.land"; second="minecraft:item.trident.hit"; pitch=0.78f; }
            case "spear_dash","aerodynamics" -> { first="minecraft:entity.player.attack.sweep"; second="minecraft:entity.ender_dragon.flap"; pitch=1.18f; }
            case "bounce","doublejump" -> { first="minecraft:entity.slime.jump"; second="minecraft:block.slime_block.fall"; pitch=1.22f; }
            case "hook" -> { first="minecraft:entity.fishing_bobber.retrieve"; second="minecraft:entity.arrow.shoot"; pitch=1.08f; }
            case "phantomblade","shadow" -> { first="minecraft:entity.phantom.ambient"; second="minecraft:entity.enderman.teleport"; pitch=0.92f; }
            case "titanshield","reflection" -> { first="minecraft:item.shield.block"; second="minecraft:block.anvil.land"; pitch=0.82f; }
            case "secondwind" -> { first="minecraft:entity.player.levelup"; second="minecraft:block.beacon.power_select"; pitch=1.12f; }
            case "softsteps","sneak" -> { first="minecraft:block.wool.step"; second="minecraft:block.amethyst_block.chime"; volume=0.55f; pitch=1.25f; }
            case "immunity" -> { first="minecraft:block.fire.extinguish"; second="minecraft:entity.experience_orb.pickup"; pitch=1.18f; }
            case "lavawalker" -> { first="minecraft:block.lava.pop"; second="minecraft:block.basalt.break"; pitch=0.82f; }
            case "magnet","experience" -> { first="minecraft:entity.experience_orb.pickup"; second="minecraft:block.amethyst_block.chime"; pitch=1.35f; }
            case "woodcutter" -> { first="minecraft:block.wood.break"; second="minecraft:block.wood.hit"; pitch=0.9f; }
            case "drill","veinminer","haste","autosmelt" -> { first="minecraft:block.deepslate.break"; second="minecraft:block.anvil.use"; pitch=0.88f; }
            case "wings","hover" -> { first="minecraft:item.elytra.flying"; second="minecraft:entity.phantom.flap"; pitch=1.12f; }
            default -> { first="minecraft:block.amethyst_block.chime"; pitch=1.0f; }
        }
        player.playSound(location,first,volume,pitch);
        if(second!=null) player.playSound(location,second,volume*0.48f,Math.min(2.0f,pitch*1.12f));
    }

    private void visual(Player p,String key,Location l) {
        World w=l.getWorld(); if(w==null)return;
        Spec activated=spec(key);
        Deque<String> history=activationHistory.computeIfAbsent(p.getUniqueId(),u->new ArrayDeque<>());
        history.addFirst("§7"+new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date())+" §8• §d"+(activated==null?key:specName(activated)));
        while(history.size()>10) history.removeLast();
        if(!effectsEnabled.getOrDefault(p.getUniqueId(),true)) return;
        int uses=awakeningCounts.computeIfAbsent(p.getUniqueId(),u->new HashMap<>()).merge(key,1,Integer::sum);
        if(uses==50 || uses==150 || uses==300) {
            String rank=uses==50?"I":uses==150?"II":"III";
            p.sendTitle("§5✦ §dПробуждение "+rank,"§f"+(activated==null?key:specName(activated))+" §8• §7"+uses+" активаций",8,45,15);
            p.playSound(p.getLocation(),Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,.55f,.75f);
        }
        switch(key) {
            case "moon" -> { w.spawnParticle(Particle.END_ROD,l,18,.35,.55,.35,.025); spawnVoidRing(l,.8,Color.fromRGB(222,224,255),24); playEnchantSound(p,key,l); }
            case "chameleon" -> { w.spawnParticle(Particle.HAPPY_VILLAGER,l,16,.4,.5,.4,.025); w.spawnParticle(Particle.DUST,l,12,.35,.45,.35,0,new Particle.DustOptions(Color.fromRGB(150,93,255),1.1f)); playEnchantSound(p,key,l); }
            case "berserk" -> { w.spawnParticle(Particle.ANGRY_VILLAGER,l,8,.3,.4,.3,.02); w.spawnParticle(Particle.CRIT,l,16,.35,.45,.35,.1); playEnchantSound(p,key,l); }
            case "godslayer","worldeater" -> { w.spawnParticle(Particle.SOUL_FIRE_FLAME,l,26,.45,.6,.45,.035); w.spawnParticle(Particle.FLASH,l,1,0,0,0,0); spawnVoidRing(l,1.05,Color.fromRGB(135,39,214),32); spawnVoidRing(l,.62,Color.fromRGB(230,226,255),20); playEnchantSound(p,key,l); }
            case "immortality" -> { w.spawnParticle(Particle.TOTEM_OF_UNDYING,l,35,.4,.6,.4,.08); spawnVoidRing(l,.85,Color.fromRGB(244,221,255),26); playEnchantSound(p,key,l); }
            case "reflection","secondwind","unbreakable","lightaura","shove","bloodsucker","titanshield" -> { w.spawnParticle(Particle.FLASH,l,1,0,0,0,0); w.spawnParticle(Particle.ENCHANT,l,22,.5,.5,.5,.45); spawnVoidRing(l,.75,Color.fromRGB(161,75,255),24); playEnchantSound(p,key,l); }
            case "woodcutter" -> { w.spawnParticle(Particle.CRIT,l,24,.4,.6,.4,.1); w.spawnParticle(Particle.BLOCK,l,10,.3,.4,.3,.1,Material.OAK_LOG.createBlockData()); playEnchantSound(p,key,l); }
            case "cleave","axemaster" -> { w.spawnParticle(Particle.SWEEP_ATTACK,l,4,.35,.4,.35,0); w.spawnParticle(Particle.CRIT,l,14,.3,.45,.3,.08); playEnchantSound(p,key,l); }
            case "stun" -> { w.spawnParticle(Particle.ELECTRIC_SPARK,l,25,.4,.6,.4,.1); w.spawnParticle(Particle.FLASH,l,1,0,0,0,0); playEnchantSound(p,key,l); }
            case "heaviness" -> { w.spawnParticle(Particle.BLOCK,l,20,.3,.4,.3,.1,Material.ANVIL.createBlockData()); w.spawnParticle(Particle.CLOUD,l,12,.25,.25,.25,.02); playEnchantSound(p,key,l); }
            case "fish","diver" -> { w.spawnParticle(Particle.BUBBLE,l,22,.4,.5,.4,.04); w.spawnParticle(Particle.SPLASH,l,10,.3,.25,.3,.04); playEnchantSound(p,key,l); }
            case "magnet" -> { w.spawnParticle(Particle.ENCHANT,l,22,.3,.4,.3,.5); spawnVoidRing(l,.58,Color.fromRGB(189,151,255),20); playEnchantSound(p,key,l); }
            case "lavawalker" -> { w.spawnParticle(Particle.FLAME,l,18,.5,.1,.5,.02); w.spawnParticle(Particle.LAVA,l,5,.3,.05,.3,0); playEnchantSound(p,key,l); }
            case "waterwalker" -> { w.spawnParticle(Particle.SPLASH,l,20,.5,.2,.5,.1); w.spawnParticle(Particle.BUBBLE,l,12,.4,.2,.4,.03); playEnchantSound(p,key,l); }
            case "blackhole" -> { w.spawnParticle(Particle.PORTAL,l,45,.5,.7,.5,.3); w.spawnParticle(Particle.REVERSE_PORTAL,l,28,.4,.6,.4,.12); spawnVoidRing(l,.9,Color.fromRGB(89,24,135),32); playEnchantSound(p,key,l); }
            case "hook" -> { w.spawnParticle(Particle.CRIT,l,18,.3,.5,.3,.1); w.spawnParticle(Particle.SWEEP_ATTACK,l,2,.2,.2,.2,0); playEnchantSound(p,key,l); }
            case "bounce","doublejump","wings","hover" -> { w.spawnParticle(Particle.CLOUD,l,24,.4,.2,.4,.08); w.spawnParticle(Particle.END_ROD,l,10,.3,.25,.3,.02); playEnchantSound(p,key,l); }
            case "thunder" -> { w.spawnParticle(Particle.ELECTRIC_SPARK,l,35,.5,.7,.5,.2); w.spawnParticle(Particle.FLASH,l,1,0,0,0,0); playEnchantSound(p,key,l); }
            case "phantomblade","shadow" -> { w.spawnParticle(Particle.SMOKE,l,24,.4,.6,.4,.03); w.spawnParticle(Particle.REVERSE_PORTAL,l,12,.25,.35,.25,.08); playEnchantSound(p,key,l); }
            case "frost" -> { w.spawnParticle(Particle.SNOWFLAKE,l,26,.3,.5,.3,.04); spawnVoidRing(l,.7,Color.fromRGB(158,222,255),22); playEnchantSound(p,key,l); }
            case "venom","immunity" -> { w.spawnParticle(Particle.SPORE_BLOSSOM_AIR,l,18,.3,.5,.3,.02); w.spawnParticle(Particle.DUST,l,12,.3,.45,.3,0,new Particle.DustOptions(Color.fromRGB(85,194,93),1.0f)); playEnchantSound(p,key,l); }
            case "dragonbreath" -> { w.spawnParticle(Particle.DRAGON_BREATH,l,34,.4,.5,.4,.025); w.spawnParticle(Particle.FLAME,l,12,.3,.3,.3,.015); playEnchantSound(p,key,l); }
            case "sniper","armorbreaker","execution" -> { w.spawnParticle(Particle.CRIT,l,24,.35,.5,.35,.08); w.spawnParticle(Particle.SWEEP_ATTACK,l,3,.2,.25,.2,0); playEnchantSound(p,key,l); }
            case "radar","owl","wisdom" -> { w.spawnParticle(Particle.END_ROD,l,14,.3,.55,.3,.015); w.spawnParticle(Particle.ENCHANT,l,18,.35,.5,.35,.25); playEnchantSound(p,key,l); }
            case "softsteps","sneak" -> { w.spawnParticle(Particle.CLOUD,l,12,.25,.08,.25,.01); w.spawnParticle(Particle.DUST,l,8,.25,.1,.25,0,new Particle.DustOptions(Color.fromRGB(181,159,220),.8f)); playEnchantSound(p,key,l); }
            case "haste","experience","autosmelt","veinminer","drill" -> { w.spawnParticle(Particle.BLOCK,l,22,.35,.45,.35,.1,Material.DEEPSLATE.createBlockData()); w.spawnParticle(Particle.CRIT,l,8,.2,.25,.2,.04); playEnchantSound(p,key,l); }
            case "spear_dash","aerodynamics" -> { w.spawnParticle(Particle.CLOUD,l,24,.35,.25,.35,.045); w.spawnParticle(Particle.END_ROD,l,14,.3,.35,.3,.02); playEnchantSound(p,key,l); }
            case "vampirism" -> { w.spawnParticle(Particle.DUST,l,28,.32,.58,.32,0,new Particle.DustOptions(Color.fromRGB(154,5,28),1.25f)); w.spawnParticle(Particle.CRIT,l,7,.18,.32,.18,.015); spawnVoidRing(l,.62,Color.fromRGB(154,5,28),22); playEnchantSound(p,key,l); }
            default -> { w.spawnParticle(Particle.ENCHANT,l,14,.3,.4,.3,.5); playEnchantSound(p,key,l); }
        }
        if(Set.of("vampirism","bloodsucker","frost","venom","execution","thunder","dragonbreath","berserk","godslayer","worldeater","blackhole","phantomblade","shadow","immortality","reflection","secondwind","unbreakable","lightaura","titanshield").contains(key)) animateCombatEffect(p,key,l);
        if(Set.of("blackhole","frost","venom","vampirism","bloodsucker","thunder","execution","berserk","godslayer","worldeater","immortality","reflection","secondwind","unbreakable","lightaura","titanshield","phantomblade","shadow").contains(key)) spawnVoxelScene(key,l);
        Color signature = switch(key) {
            case "venom","immunity" -> Color.fromRGB(93,220,104);
            case "execution" -> Color.fromRGB(255,126,48);
            case "thunder","stun","experience" -> Color.fromRGB(255,226,92);
            case "berserk","bloodsucker" -> Color.fromRGB(210,38,58);
            case "spear_dash","aerodynamics" -> Color.fromRGB(111,220,255);
            case "bounce","doublejump" -> Color.fromRGB(188,116,255);
            case "hook","waterwalker","diver","fish" -> Color.fromRGB(85,184,255);
            case "phantomblade","shadow" -> Color.fromRGB(143,105,205);
            case "reflection","titanshield","unbreakable" -> Color.fromRGB(220,224,238);
            case "secondwind","lightaura" -> Color.fromRGB(157,255,203);
            case "softsteps","sneak" -> Color.fromRGB(196,169,235);
            case "woodcutter" -> Color.fromRGB(160,112,72);
            case "heaviness" -> Color.fromRGB(151,160,174);
            case "wings","hover" -> Color.fromRGB(210,240,255);
            case "chameleon" -> Color.fromRGB(175,110,255);
            default -> null;
        };
        if(signature!=null) {
            spawnVoidRing(l,.48,signature,20);
            spawnVoidRing(l,.76,signature,28);
            w.spawnParticle(Particle.DUST,l.clone().add(0,.72,0),18,.28,.48,.28,0,new Particle.DustOptions(signature,1.15f));
        }
    }
    private void animateCombatEffect(Player source,String key,Location origin) {
        World world=origin.getWorld(); if(world==null)return;
        final Color tint=switch(key) {
            case "vampirism","bloodsucker","berserk" -> Color.fromRGB(190,24,52);
            case "frost" -> Color.fromRGB(120,210,255);
            case "venom" -> Color.fromRGB(80,220,90);
            case "thunder","godslayer","lightaura" -> Color.fromRGB(255,205,75);
            case "execution","dragonbreath" -> Color.fromRGB(255,105,55);
            case "reflection","titanshield","unbreakable" -> Color.fromRGB(205,225,255);
            case "secondwind","immortality" -> Color.fromRGB(145,255,190);
            case "worldeater","blackhole","phantomblade","shadow" -> Color.fromRGB(135,65,220);
            default -> Color.fromRGB(190,150,255);
        };
        new BukkitRunnable() {
            int tick=0;
            @Override public void run() {
                if(tick>=9 || !origin.isWorldLoaded()) { cancel(); return; }
                Location c=origin.clone().add(0,0.85,0);
                double radius=0.28+tick*0.105;
                switch(key) {
                    case "vampirism","bloodsucker" -> {
                        for(int n=0;n<5;n++){double a=(tick*0.62)+(Math.PI*2*n/5);world.spawnParticle(Particle.DUST,c.clone().add(Math.cos(a)*radius,Math.sin(a*1.7)*.24,Math.sin(a)*radius),1,0,0,0,0,new Particle.DustOptions(tint,1.35f));}
                        world.spawnParticle(Particle.HEART,c,1,.12,.18,.12,0);
                    }
                    case "frost" -> {
                        for(int n=0;n<8;n++){double a=Math.PI*2*n/8+tick*.18;world.spawnParticle(Particle.SNOWFLAKE,c.clone().add(Math.cos(a)*radius,0,Math.sin(a)*radius),1,0,0,0,0);}
                        if(tick==5)world.spawnParticle(Particle.BLOCK,c,18,.35,.35,.35,.08,Material.ICE.createBlockData());
                    }
                    case "venom" -> {
                        world.spawnParticle(Particle.SPORE_BLOSSOM_AIR,c,5,.24,.18,.24,.005);
                        world.spawnParticle(Particle.DUST,c,7,.28,.24,.28,0,new Particle.DustOptions(tint,1.15f));
                    }
                    case "thunder","godslayer","execution" -> {
                        for(int n=0;n<3;n++){double a=tick*.45+n*Math.PI*2/3;world.spawnParticle(Particle.ELECTRIC_SPARK,c.clone().add(Math.cos(a)*radius,Math.sin(tick*.35)*.25,Math.sin(a)*radius),2,.02,.08,.02,.02);}
                        if(tick==2||tick==6)world.spawnParticle(Particle.FLASH,c,1,0,0,0,0);
                    }
                    case "reflection","titanshield","unbreakable","lightaura" -> {
                        double r=0.55+tick*.08;
                        for(int n=0;n<18;n++){double a=Math.PI*2*n/18;world.spawnParticle(Particle.DUST,c.clone().add(Math.cos(a)*r,Math.sin(a)*r,0),1,0,0,0,0,new Particle.DustOptions(tint,1.2f));}
                        world.spawnParticle(Particle.ENCHANT,c,8,.42,.45,.42,.25);
                    }
                    case "secondwind","immortality" -> {
                        double a=tick*.7;world.spawnParticle(Particle.HEART,c.clone().add(Math.cos(a)*radius,.15+tick*.035,Math.sin(a)*radius),1,0,0,0,0);
                        world.spawnParticle(Particle.TOTEM_OF_UNDYING,c,3,.22,.28,.22,.04);
                    }
                    case "berserk" -> {
                        double r=.35+tick*.09;for(int n=0;n<12;n++){double a=Math.PI*2*n/12;world.spawnParticle(Particle.DUST,c.clone().add(Math.cos(a)*r,0,Math.sin(a)*r),1,0,0,0,0,new Particle.DustOptions(tint,1.25f));}
                        world.spawnParticle(Particle.FLAME,c,2,.28,.28,.28,.01);
                    }
                    case "worldeater","blackhole" -> {
                        for(int n=0;n<12;n++){double a=tick*.55+n*Math.PI*2/12;world.spawnParticle(Particle.REVERSE_PORTAL,c.clone().add(Math.cos(a)*radius,.04*tick,Math.sin(a)*radius),1,0,0,0,.015);}
                        if(tick==4)spawnBlackHoleCube(c);
                    }
                    case "dragonbreath" -> world.spawnParticle(Particle.DRAGON_BREATH,c,12,.38,.28,.38,.015);
                    case "phantomblade","shadow" -> {
                        world.spawnParticle(Particle.SMOKE,c,8,.24,.32,.24,.015);
                        world.spawnParticle(Particle.REVERSE_PORTAL,c,5,.28,.35,.28,.02);
                    }
                    default -> {
                        for(int n=0;n<10;n++){double a=Math.PI*2*n/10+tick*.22;world.spawnParticle(Particle.DUST,c.clone().add(Math.cos(a)*radius,0,Math.sin(a)*radius),1,0,0,0,0,new Particle.DustOptions(tint,1.0f));}
                    }
                }
                tick++;
            }
        }.runTaskTimer(this,0L,1L);
    }

    private void sendHelp(CommandSender sender, boolean admin) {
        sender.sendMessage("§5§lVoidEnchantments §8— §fкоманды");
        sender.sendMessage("§d/enchants §7— открыть каталог");
        sender.sendMessage("§d/enchants list §7— список чар");
        sender.sendMessage("§d/enchants info <чар> §7— описание");
        sender.sendMessage("§d/enchants apply §7— как применить книгу");
        if(admin) {
            sender.sendMessage("§c/ve give <игрок> <чар> [уровень]");
            sender.sendMessage("§c/ve giveall <чар> [уровень]");
            sender.sendMessage("§c/ve remove <игрок> <чар> §8| §c/ve book <чар> [уровень]");
            sender.sendMessage("§c/ve list §8| §c/ve info <чар> §8| §c/ve debug <игрок>");
            sender.sendMessage("§c/ve drops §8| §c/ve admin §8| §c/ve reload §8| §c/ve help");
        }
    }
    private Spec findSpec(String input) {
        if(input==null) return null;
        Spec byKey=spec(input);
        if(byKey!=null) return byKey;
        return specs.stream().filter(x->x.name().equalsIgnoreCase(input)).findFirst().orElse(null);
    }
    private int parseLevel(Spec s,String raw) {
        if(!s.leveled()) return 1;
        try { return Math.max(1,Math.min(maxLevel(s),Integer.parseInt(raw))); }
        catch(Exception ignored) { return 1; }
    }
    private void giveBook(Player p,Spec s,int lvl) {
        Map<Integer,ItemStack> rest=p.getInventory().addItem(book(s,lvl));
        rest.values().forEach(it->p.getWorld().dropItemNaturally(p.getLocation(),it));
        p.playSound(p.getLocation(),"minecraft:ui.toast.challenge_complete",0.75f,1.15f);
        p.playSound(p.getLocation(),"minecraft:entity.experience_orb.pickup",0.55f,1.2f);
    }
    private void sendSpecInfo(CommandSender sender,Spec s) {
        int tier=rarityTier(s);
        sender.sendMessage("§5✦ §l"+specName(s)+" §8("+s.key()+")");
        sender.sendMessage("§7Описание: §f"+specDescription(s));
        sender.sendMessage("§7Предмет: §f"+appliesTo(s));
        sender.sendMessage("§7Редкость: "+rarityColor(tier)+rarityName(tier));
        sender.sendMessage("§7Уровни: §f"+(s.leveled()?"I–"+roman(maxLevel(s)):"без уровней"));
        sender.sendMessage("§7Статус: "+(isEnabled(s.key())?"§aвключено":"§cотключено"));
    }
    private boolean removeEnchant(Player p,Spec s) {
        boolean changed=false;
        for(ItemStack item:p.getInventory().getContents()) changed|=removeEnchantFromItem(item,s);
        for(ItemStack item:p.getInventory().getArmorContents()) changed|=removeEnchantFromItem(item,s);
        changed|=removeEnchantFromItem(p.getInventory().getItemInOffHand(),s);
        p.updateInventory();
        if(changed) p.playSound(p.getLocation(),"minecraft:block.grindstone.use",0.85f,1.0f);
        return changed;
    }
    private boolean removeEnchantFromItem(ItemStack item,Spec s) {
        if(item==null || !item.hasItemMeta() || !hasEnchant(item,s.key())) return false;
        ItemMeta meta=item.getItemMeta();
        meta.getPersistentDataContainer().remove(itemEnchantKey(s.key()));
        if(s.key().equals(meta.getPersistentDataContainer().get(enchantKey,PersistentDataType.STRING))) {
            meta.getPersistentDataContainer().remove(enchantKey);
            meta.getPersistentDataContainer().remove(levelKey);
        }
        List<String> lore=meta.hasLore()?new ArrayList<>(meta.getLore()):new ArrayList<>();
        lore.removeIf(line->ChatColor.stripColor(line).startsWith(specName(s)));
        meta.setLore(lore.isEmpty()?null:lore);
        item.setItemMeta(meta);
        return true;
    }
    private void openAdmin(Player p) {
        Inventory inv=Bukkit.createInventory(null,54,ADMIN_TITLE);
        for(int i=0;i<specs.size() && i<45;i++) {
            Spec s=specs.get(i); int tier=rarityTier(s);
            ItemStack icon=new ItemStack(s.icon()); ItemMeta m=icon.getItemMeta();
            m.setDisplayName(gradient(specName(s),tier));
            m.setLore(List.of("§7"+specDescription(s),"§7Предмет: §f"+appliesTo(s),
                "§7Редкость: "+rarityColor(tier)+rarityName(tier),
                "§7Эффект: "+(isEnabled(s.key())?"§aВКЛЮЧЁН":"§cВЫКЛЮЧЕН"),
                "§7Шанс выбора: §f"+chanceText(s),
                "","§eЛКМ §7— вкл./выкл.",
                "§eShift + ЛКМ §7— повысить шанс на 0,05%",
                "§eShift + ПКМ §7— понизить шанс на 0,05%"));
            m.getPersistentDataContainer().set(enchantKey,PersistentDataType.STRING,s.key());
            icon.setItemMeta(m); inv.setItem(i,icon);
        }
        ItemStack book=new ItemStack(Material.ENCHANTED_BOOK); ItemMeta bm=book.getItemMeta();
        bm.setDisplayName("§dЖурнал выпадения книг");
        bm.setLore(List.of("§7Используй §f/ve drops","§7Статистика выпадений с жителей"));
        book.setItemMeta(bm); inv.setItem(49,book);
        ItemStack barrier=new ItemStack(Material.BARRIER); ItemMeta cm=barrier.getItemMeta(); cm.setDisplayName("§cЗакрыть"); barrier.setItemMeta(cm); inv.setItem(53,barrier);
        p.openInventory(inv);
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        String sub=args.length==0?"":args[0].toLowerCase(Locale.ROOT);
        boolean admin=sender.hasPermission("voidenchants.admin");
        if(sub.isEmpty()) {
            if(sender instanceof Player p) openCategories(p); else sendHelp(sender,admin);
            return true;
        }
        if(sub.equals("help")) { sendHelp(sender,admin); return true; }
        if(sub.equals("list")) {
            sender.sendMessage("§5§lVoidMain §8| §dКастомные зачарования:");
            for(Spec s:specs) sender.sendMessage(rarityColor(rarityTier(s))+"• "+specName(s)+" §8— §7"+s.key()+" §8| §f"+appliesTo(s));
            return true;
        }
        if(sub.equals("info") || sub.equals("инфо")) {
            if(args.length<2) { sender.sendMessage("§cИспользование: /enchants info <чар>"); return true; }
            Spec s=findSpec(args[1]); if(s==null) sender.sendMessage("§cТакое зачарование не найдено."); else sendSpecInfo(sender,s);
            return true;
        }
        if(sub.equals("effects")) {
            if(!(sender instanceof Player p)) { sender.sendMessage("§cКоманда только для игрока."); return true; }
            boolean enabled=!effectsEnabled.getOrDefault(p.getUniqueId(),true); effectsEnabled.put(p.getUniqueId(),enabled);
            p.sendMessage(enabled?"§5✦ §dЭффекты зачарований включены.":"§8✦ §7Частицы и звуки зачарований отключены."); return true;
        }
        if(sub.equals("history")) {
            if(!(sender instanceof Player p)) { sender.sendMessage("§cКоманда только для игрока."); return true; }
            Deque<String> entries=activationHistory.getOrDefault(p.getUniqueId(),new ArrayDeque<>());
            p.sendMessage("§5§lVoidMain §8| §dПоследние срабатывания");
            if(entries.isEmpty()) p.sendMessage("§7Пока нет записей."); else entries.forEach(p::sendMessage); return true;
        }
        if(sub.equals("apply")) {
            sender.sendMessage("§d✦ §fКак применить кастомное зачарование:");
            sender.sendMessage("§71. §fВозьми книгу в левую или правую ячейку наковальни.");
            sender.sendMessage("§72. §fПоложи подходящий предмет во вторую ячейку.");
            sender.sendMessage("§73. §fЗабери результат за уровни опыта.");
            sender.sendMessage("§8Крылья несовместимы с другими чарами на элитрах.");
            return true;
        }
        if(!admin) { sender.sendMessage("§cНет прав. Админ-команды доступны только операторам."); return true; }
        switch(sub) {
            case "adminbook" -> {
                if(args.length<3) { sender.sendMessage("§c/ve adminbook <игрок> <админское зачарование>"); return true; }
                Player target=Bukkit.getPlayerExact(args[1]); Spec s=findSpec(args[2]);
                if(target==null || s==null || !adminOnly(s.key())) { sender.sendMessage("§cУкажи онлайн-игрока и зачарование редкости 0."); return true; }
                giveBook(target,s,1); sender.sendMessage("§cАдминская книга выдана "+target.getName()+".");
            }
            case "give" -> {
                if(args.length<3) { sender.sendMessage("§c/ve give <игрок> <чар> [уровень]"); return true; }
                Player target=Bukkit.getPlayerExact(args[1]); Spec s=findSpec(args[2]);
                if(target==null || s==null) { sender.sendMessage("§cИгрок или зачарование не найдено."); return true; }
                int lvl=args.length>3?parseLevel(s,args[3]):1; giveBook(target,s,lvl);
                sender.sendMessage(gradient("Выдано игроку "+target.getName()+": "+specName(s)+" "+roman(lvl)));
            }
            case "giveall" -> {
                if(args.length<2) { sender.sendMessage("§c/ve giveall <чар> [уровень]"); return true; }
                Spec s=findSpec(args[1]); if(s==null) { sender.sendMessage("§cЗачарование не найдено."); return true; }
                int lvl=args.length>2?parseLevel(s,args[2]):1;
                for(Player target:Bukkit.getOnlinePlayers()) giveBook(target,s,lvl);
                sender.sendMessage("§aКнига выдана всем онлайн: §f"+Bukkit.getOnlinePlayers().size()+" §7игрокам.");
            }
            case "book" -> {
                if(!(sender instanceof Player p)) { sender.sendMessage("§cКоманда только для игрока."); return true; }
                if(args.length<2) { sender.sendMessage("§c/ve book <чар> [уровень]"); return true; }
                Spec s=findSpec(args[1]); if(s==null) { sender.sendMessage("§cЗачарование не найдено."); return true; }
                giveBook(p,s,args.length>2?parseLevel(s,args[2]):1); sender.sendMessage("§aКнига выдана.");
            }
            case "strip" -> {
                if(!(sender instanceof Player p)) { sender.sendMessage("§cКоманда только для игрока."); return true; }
                if(args.length<2) { sender.sendMessage("§c/ve strip <чар> — снять чар с предмета в руке"); return true; }
                Spec selected=findSpec(args[1]); ItemStack hand=p.getInventory().getItemInMainHand();
                if(selected==null || hand.getType()==Material.AIR) { sender.sendMessage("§cУкажи зачарование и держи предмет в руке."); return true; }
                boolean removed=removeEnchantFromItem(hand,selected); p.updateInventory();
                if(removed) p.playSound(p.getLocation(),"voidmain:enchant_remove",0.8f,1.0f);
                sender.sendMessage(removed?"§aЗачарование снято с предмета в руке.":"§eНа предмете нет этого зачарования.");
            }
            case "remove" -> {
                if(args.length<3) { sender.sendMessage("§c/ve remove <игрок> <чар>"); return true; }
                Player target=Bukkit.getPlayerExact(args[1]); Spec s=findSpec(args[2]);
                if(target==null || s==null) { sender.sendMessage("§cИгрок или зачарование не найдено."); return true; }
                sender.sendMessage(removeEnchant(target,s)?"§aЗачарование удалено со всех предметов игрока.":"§eУ игрока такого зачарования нет.");
            }
            case "reload" -> { reloadConfig(); sender.sendMessage("§aКонфигурация VoidEnchantments перезагружена."); }
            case "debug" -> {
                if(args.length<2) { sender.sendMessage("§c/ve debug <игрок>"); return true; }
                Player target=Bukkit.getPlayerExact(args[1]); if(target==null) { sender.sendMessage("§cИгрок не найден или не в сети."); return true; }
                sender.sendMessage("§5§lVoidEnchantments §8| §fПредметы "+target.getName());
                ItemStack[] all=target.getInventory().getContents();
                for(int i=0;i<all.length;i++) if(all[i]!=null && !enchantKeys(all[i]).isEmpty())
                    sender.sendMessage("§7Слот "+i+" §8("+all[i].getType()+"): §f"+String.join(", ",enchantKeys(all[i])));
                for(ItemStack it:target.getInventory().getArmorContents()) if(it!=null && !enchantKeys(it).isEmpty())
                    sender.sendMessage("§7Броня §8("+it.getType()+"): §f"+String.join(", ",enchantKeys(it)));
            }
            case "drops" -> {
                sender.sendMessage("§5§lVoidMain §8| §dВыпадение книг с жителей");
                for(Spec s:specs) {
                    int count=getConfig().getInt("drop-count."+s.key(),0);
                    sender.sendMessage(rarityColor(rarityTier(s))+specName(s)+" §8— §f"+count+" §7шт. §8| §7шанс "+chanceText(s));
                }
            }
            case "admin" -> {
                if(sender instanceof Player p) openAdmin(p); else sender.sendMessage("§cАдмин-панель доступна только в игре.");
            }
            default -> sender.sendMessage("§cНеизвестная команда. Используй /ve help");
        }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        boolean admin=sender.hasPermission("voidenchants.admin");
        List<String> roots=new ArrayList<>(List.of("help","list","info","apply","effects","history"));
        if(admin) roots.addAll(List.of("give","giveall","remove","strip","book","adminbook","reload","debug","drops","admin"));
        if(args.length==1) return roots.stream().filter(x->x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        String sub=args[0].toLowerCase(Locale.ROOT);
        if(!admin && !List.of("info","apply","list","help").contains(sub)) return List.of();
        if(args.length==2 && List.of("give","remove","debug").contains(sub) && admin)
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(x->x.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        if((args.length==2 && List.of("info","giveall","book","strip").contains(sub)) || (args.length==3 && List.of("give","remove").contains(sub))) {
            return specs.stream().map(Spec::key).filter(x->x.startsWith(args[args.length-1].toLowerCase(Locale.ROOT))).toList();
        }
        if((args.length==4 && sub.equals("give")) || (args.length==3 && List.of("giveall","book").contains(sub))) return List.of("1","2","3","4");
        return List.of();
    }
    private String categoryName(String key) {
        return categories.stream().filter(c->c.key().equals(key)).map(Category::title).findFirst().orElse("Зачарования");
    }
    private boolean belongsToCategory(Spec s,String category) {
        return switch(category) {
            case "swords" -> Set.of("moon","godslayer","vampirism","frost","venom","execution","phantomblade").contains(s.key());
            case "axes" -> Set.of("woodcutter","cleave","stun","heaviness","axemaster","berserk").contains(s.key());
            case "mace" -> Set.of("thunder","blackhole","bounce","worldeater").contains(s.key());
            case "pickaxes" -> Set.of("magnet","drill","autosmelt","veinminer","haste","experience").contains(s.key());
            case "armor" -> Set.of("fish","dragonbreath","sneak","diver","immunity","softsteps","lavawalker","owl","wisdom","radar","chameleon","immortality","shadow","doublejump","waterwalker").contains(s.key());
            case "shields" -> Set.of("reflection","secondwind","unbreakable","lightaura","shove","bloodsucker","titanshield").contains(s.key());
            case "bows" -> Set.of("sniper").contains(s.key());
            case "spears" -> Set.of("spear_dash","hook","armorbreaker").contains(s.key());
            case "elytra" -> Set.of("wings","hover","aerodynamics").contains(s.key());
            default -> false;
        };
    }
    private String currentCatalogCategory(Inventory inv) {
        ItemStack tab=inv.getItem(0);
        if(tab==null || !tab.hasItemMeta()) return "swords";
        String value=tab.getItemMeta().getPersistentDataContainer().get(categoryKey,PersistentDataType.STRING);
        return value==null?"swords":value;
    }
    private int currentCatalogPage(Inventory inv) {
        ItemStack marker=inv.getItem(49);
        if(marker==null || !marker.hasItemMeta()) return 0;
        Integer value=marker.getItemMeta().getPersistentDataContainer().get(pageKey,PersistentDataType.INTEGER);
        return value==null?0:value;
    }
    private void fillMenuFrame(Inventory inv) {
        for(int slot=0;slot<inv.getSize();slot++) {
            ItemStack tile=new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta meta=tile.getItemMeta();
            meta.setDisplayName("§0 ");
            tile.setItemMeta(meta);
            inv.setItem(slot,tile);
        }
    }
    private void openCategories(Player p) {
        Inventory inv=Bukkit.createInventory(null,54,CATEGORY_TITLE);
        fillMenuFrame(inv);
        int[] slots={10,12,14,19,21,23,28,30,32};
        for(int i=0;i<categories.size();i++) {
            Category category=categories.get(i);
            ItemStack icon=new ItemStack(category.icon());
            ItemMeta meta=icon.getItemMeta();
            meta.setDisplayName("§d§l"+category.title());
            meta.setLore(List.of("§7Посмотреть чары для предмета","", "§eНажми, чтобы открыть"));
            meta.getPersistentDataContainer().set(categoryKey,PersistentDataType.STRING,category.key());
            icon.setItemMeta(meta);
            inv.setItem(slots[i],icon);
        }
        ItemStack close=new ItemStack(Material.BARRIER);
        ItemMeta cm=close.getItemMeta(); cm.setDisplayName("§cЗакрыть"); close.setItemMeta(cm);
        inv.setItem(49,close);
        p.openInventory(inv);
    }
    private boolean belongsTo(Spec spec,String category) {
        String target=appliesTo(spec);
        return switch(category) {
            case "swords" -> target.contains("Меч");
            case "axes" -> target.contains("топор") || target.contains("Топор");
            case "mace" -> target.contains("Булава");
            case "pickaxes" -> target.contains("Кирка") || target.contains("лопата");
            case "armor" -> target.contains("Шлем") || target.contains("Нагрудник") || target.contains("Поножи")
                    || target.contains("Ботинки") || target.contains("броня");
            case "shields" -> target.contains("Щит");
            case "bows" -> target.contains("Лук");
            case "spears" -> target.contains("Копьё");
            case "elytra" -> target.contains("Элитры");
            default -> false;
        };
    }
    private void openCatalog(Player p,String category,int page) {
        Category selectedCategory=categories.stream().filter(c->c.key().equals(category)).findFirst().orElse(null);
        if(selectedCategory==null) { openCategories(p); return; }
        List<Spec> visible=specs.stream().filter(x->belongsTo(x,category)).toList();
        int pageSize=36;
        int maxPage=Math.max(0,(visible.size()-1)/pageSize);
        page=Math.max(0,Math.min(page,maxPage));
        Inventory inv=Bukkit.createInventory(null,54,CATALOG_TITLE);
        fillMenuFrame(inv);
        int start=page*pageSize;
        for(int i=0;i<pageSize && start+i<visible.size();i++) {
            Spec spec=visible.get(start+i);
            ItemStack icon=new ItemStack(spec.icon()); ItemMeta meta=icon.getItemMeta();
            int tier=rarityTier(spec);
            meta.setDisplayName(gradient(specName(spec),tier));
            meta.setLore(List.of("§7"+specDescription(spec),"","§7Предмет: §f"+appliesTo(spec),
                "§7Редкость: "+rarityColor(tier)+rarityName(tier),
                "§7Уровни: §f"+(spec.leveled()?"I–"+roman(maxLevel(spec)):"Без уровней"),
                "",spec.leveled()?"§eНажми, чтобы выбрать уровень":"§eНажми, чтобы получить книгу"));
            meta.getPersistentDataContainer().set(enchantKey,PersistentDataType.STRING,spec.key());
            icon.setItemMeta(meta); inv.setItem(i,icon);
        }
        ItemStack back=new ItemStack(Material.ARROW); ItemMeta bm=back.getItemMeta();
        bm.setDisplayName("§d← Выбор предмета"); bm.setLore(List.of("§7Вернуться к категориям"));
        back.setItemMeta(bm); inv.setItem(45,back);
        ItemStack previous=new ItemStack(page>0?Material.ARROW:Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pm=previous.getItemMeta(); pm.setDisplayName(page>0?"§d← Назад":"§8Первая страница");
        pm.getPersistentDataContainer().set(pageKey,PersistentDataType.INTEGER,Math.max(0,page-1));
        pm.getPersistentDataContainer().set(categoryKey,PersistentDataType.STRING,category);
        previous.setItemMeta(pm); inv.setItem(47,previous);
        ItemStack close=new ItemStack(Material.BARRIER); ItemMeta cm=close.getItemMeta(); cm.setDisplayName("§cЗакрыть"); close.setItemMeta(cm); inv.setItem(49,close);
        ItemStack next=new ItemStack(page<maxPage?Material.ARROW:Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta nm=next.getItemMeta(); nm.setDisplayName(page<maxPage?"§dДалее →":"§8Последняя страница");
        nm.getPersistentDataContainer().set(pageKey,PersistentDataType.INTEGER,Math.min(maxPage,page+1));
        nm.getPersistentDataContainer().set(categoryKey,PersistentDataType.STRING,category);
        next.setItemMeta(nm); inv.setItem(51,next);
        ItemStack heading=new ItemStack(selectedCategory.icon()); ItemMeta hm=heading.getItemMeta();
        hm.setDisplayName("§d§l"+selectedCategory.title());
        hm.setLore(List.of("§7Страница §f"+(page+1)+"§7/§f"+(maxPage+1)));
        heading.setItemMeta(hm); inv.setItem(53,heading);
        p.openInventory(inv);
    }
    private void openLevelMenu(Player p,Spec s) {
        Inventory inv=Bukkit.createInventory(null,27,LEVEL_TITLE);
        int tier=rarityTier(s);
        for(int lvl=1;lvl<=maxLevel(s);lvl++) {
            ItemStack icon=new ItemStack(Material.ENCHANTED_BOOK); ItemMeta meta=icon.getItemMeta();
            meta.setDisplayName(gradient(specName(s),tier)+" §7"+roman(lvl));
            meta.setLore(List.of("§7"+specDescription(s),"","§7Предмет: §f"+appliesTo(s),
                "§7Редкость: "+rarityColor(tier)+rarityName(tier),
                "§7Уровень: §f"+roman(lvl),"","§eНажми, чтобы получить книгу"));
            meta.getPersistentDataContainer().set(enchantKey,PersistentDataType.STRING,s.key());
            meta.getPersistentDataContainer().set(levelKey,PersistentDataType.INTEGER,lvl);
            icon.setItemMeta(meta); inv.setItem(10+lvl,icon);
        }
        ItemStack back=new ItemStack(Material.ARROW); ItemMeta bm=back.getItemMeta();
        bm.setDisplayName("§eНазад к зачарованиям"); back.setItemMeta(bm); inv.setItem(18,back);
        ItemStack close=new ItemStack(Material.BARRIER); ItemMeta cm=close.getItemMeta();
        cm.setDisplayName("§cЗакрыть"); close.setItemMeta(cm); inv.setItem(26,close);
        p.openInventory(inv);
    }
    @EventHandler public void onCatalogDrag(InventoryDragEvent e) {
        String title = e.getView().getTitle();
        if (title.equals(ADMIN_TITLE) || title.equals(CATALOG_TITLE) || title.equals(LEVEL_TITLE) || title.equals(CATEGORY_TITLE)) e.setCancelled(true);
    }

    @EventHandler public void onCatalog(InventoryClickEvent e) {
        if(!(e.getWhoClicked() instanceof Player p)) return;
        String title=e.getView().getTitle();
        if(title.equals(ADMIN_TITLE)) {
            e.setCancelled(true);
            if(e.getRawSlot()<0 || e.getRawSlot()>=54) return;
            ItemStack clicked=e.getCurrentItem();
            if(clicked==null || clicked.getType()==Material.AIR) return;
            if(e.getRawSlot()==53) { p.closeInventory(); return; }
            String key=id(clicked); Spec selected=findSpec(key);
            if(selected==null) return;
            if(e.getClick()==ClickType.SHIFT_LEFT || e.getClick()==ClickType.SHIFT_RIGHT) {
                double delta=e.getClick()==ClickType.SHIFT_LEFT?0.05:-0.05;
                double next=Math.max(0,Math.min(100,dropChance(selected)+delta));
                getConfig().set("drop-chance."+selected.key(),next); saveConfig();
                p.sendMessage("§dШанс "+specName(selected)+": §f"+String.format(Locale.US,"%.3f",next)+"%");
            } else {
                List<String> disabled=new ArrayList<>(getConfig().getStringList("disabled-enchants"));
                if(disabled.contains(selected.key())) disabled.remove(selected.key()); else disabled.add(selected.key());
                getConfig().set("disabled-enchants",disabled); saveConfig();
                p.sendMessage("§d"+specName(selected)+": "+(isEnabled(selected.key())?"§aвключено":"§cотключено"));
            }
            openAdmin(p); return;
        }
        if(title.equals(CATEGORY_TITLE)) {
            e.setCancelled(true);
            if(e.getRawSlot()<0 || e.getRawSlot()>=54) return;
            ItemStack clicked=e.getCurrentItem(); if(clicked==null || clicked.getType()==Material.AIR) return;
            if(e.getRawSlot()==49) { p.closeInventory(); return; }
            String category=clicked.getItemMeta().getPersistentDataContainer().get(categoryKey,PersistentDataType.STRING);
            if(category!=null) { p.playSound(p.getLocation(),Sound.UI_BUTTON_CLICK,0.7f,1.2f); openCatalog(p,category,0); }
            return;
        }
        if(title.equals(LEVEL_TITLE)) {
            e.setCancelled(true);
            if (!p.hasPermission("voidenchants.admin")) {
                p.closeInventory();
                p.sendMessage("§cПолучать книги кастомных зачарований через меню может только администрация.");
                return;
            }
            if(e.getRawSlot()<0 || e.getRawSlot()>=27) return;
            ItemStack clicked=e.getCurrentItem();
            if(clicked==null || clicked.getType()==Material.AIR) return;
            if(e.getRawSlot()==26) { p.closeInventory(); return; }
            if(e.getRawSlot()==18) { openCategories(p); return; }
            Spec selected=findSpec(id(clicked));
            if(selected==null || !selected.leveled()) return;
            int lvl=level(clicked);
            if(lvl<1 || lvl>maxLevel(selected)) return;
            giveBook(p,selected,lvl);
            p.playSound(p.getLocation(),Sound.UI_BUTTON_CLICK,0.8f,1.4f);
            p.sendMessage(gradient("✦ Получена книга: "+specName(selected)+" "+roman(lvl)));
            p.closeInventory(); return;
        }
        if(!title.equals(CATALOG_TITLE)) return;
        e.setCancelled(true);
        if (!p.hasPermission("voidenchants.admin")) {
            if (e.getRawSlot() >= 0 && e.getRawSlot() < 54) {
                ItemStack preview = e.getCurrentItem();
                if (preview != null && preview.getType() != Material.AIR) {
                    Spec info = findSpec(id(preview));
                    if (info != null) sendSpecInfo(p, info);
                }
            }
            return;
        }
        if(e.getRawSlot()<0 || e.getRawSlot()>=54) return;
        ItemStack clicked=e.getCurrentItem();
        if(clicked==null || clicked.getType()==Material.AIR) return;
        if(e.getRawSlot()==49 && clicked.getType()==Material.BARRIER) { p.closeInventory(); return; }
        if(e.getRawSlot()==45) { openCategories(p); return; }
        if(e.getRawSlot()==47 || e.getRawSlot()==51) {
            Integer targetPage=clicked.getItemMeta().getPersistentDataContainer().get(pageKey,PersistentDataType.INTEGER);
            String category=clicked.getItemMeta().getPersistentDataContainer().get(categoryKey,PersistentDataType.STRING);
            if(targetPage!=null && category!=null) {
                p.playSound(p.getLocation(),Sound.UI_BUTTON_CLICK,0.7f,1.2f);
                openCatalog(p,category,targetPage);
            }
            return;
        }
        Spec selected=findSpec(id(clicked));
        if(selected==null) return;
        if(selected.leveled()) { openLevelMenu(p,selected); return; }
        giveBook(p,selected,1);
        p.playSound(p.getLocation(),Sound.UI_BUTTON_CLICK,0.8f,1.5f);
        p.sendMessage(gradient("✦ Получена книга: "+specName(selected)));
        p.closeInventory();
    }

    @EventHandler public void onCustomEnchantApply(InventoryClickEvent e) {
        if(e.getRawSlot()!=2 || e.getView().getTopInventory().getType()!=InventoryType.ANVIL) return;
        if(!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack left=e.getView().getTopInventory().getItem(0);
        ItemStack right=e.getView().getTopInventory().getItem(1);
        if(id(left)==null && id(right)==null) return;
        ItemStack result=e.getView().getTopInventory().getItem(2);
        if(result!=null && result.getType()!=Material.AIR && id(result)!=null) {
            p.playSound(p.getLocation(),"minecraft:block.enchantment_table.use",0.9f,1.08f);
            p.playSound(p.getLocation(),"minecraft:block.amethyst_block.chime",0.5f,1.3f);
        } else {
            p.playSound(p.getLocation(),"minecraft:entity.villager.no",0.9f,0.82f);
            p.playSound(p.getLocation(),"minecraft:entity.villager.no",0.55f,0.85f);
        }
    }

    private final Map<UUID,Float> frozenWalkSpeeds=new HashMap<>();
    private final Map<UUID,Integer> frozenPlayerTasks=new HashMap<>();
    private void immobilize(LivingEntity target) { immobilize(target,40L); }
    private void immobilize(LivingEntity target,long ticks) {
        Location center=target.getLocation().add(0,0.9,0);
        if(target instanceof Player victim) {
            UUID id=victim.getUniqueId();
            Integer previous=frozenPlayerTasks.remove(id);
            if(previous!=null) Bukkit.getScheduler().cancelTask(previous);
            frozenWalkSpeeds.putIfAbsent(id,victim.getWalkSpeed());
            victim.setWalkSpeed(0f);
            victim.setVelocity(new Vector(0,0,0));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,(int)ticks,10,true,false),true);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,(int)ticks,128,true,false),true);
            int task=Bukkit.getScheduler().scheduleSyncDelayedTask(this,()->{
                if(victim.isOnline()) {
                    victim.setWalkSpeed(frozenWalkSpeeds.getOrDefault(id,0.2f));
                    victim.removePotionEffect(PotionEffectType.SLOWNESS);
                    victim.removePotionEffect(PotionEffectType.JUMP_BOOST);
                    victim.setVelocity(new Vector(0,0,0));
                }
                frozenWalkSpeeds.remove(id);
                frozenPlayerTasks.remove(id);
            },ticks);
            frozenPlayerTasks.put(id,task);
        } else if(target instanceof Mob mob) {
            boolean old=mob.hasAI(); mob.setAI(false); mob.setVelocity(new Vector(0,0,0));
            Bukkit.getScheduler().runTaskLater(this,()->{if(mob.isValid())mob.setAI(old);},ticks);
        } else {
            target.setVelocity(new Vector(0,0,0));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,(int)ticks,10,true,false),true);
        }
        target.getWorld().spawnParticle(Particle.PORTAL,center,42,0.45,0.55,0.45,0.12);
        target.getWorld().spawnParticle(Particle.DUST,center,18,0.35,0.45,0.35,0,new Particle.DustOptions(Color.fromRGB(86,24,145),1.25f));
        spawnBlackHoleCube(center);
    }
    private void spawnBlackHoleCube(Location center) {
        World world=center.getWorld(); if(world==null)return;
        Color edge=Color.fromRGB(104,42,174);
        double half=0.92;
        for(int step=0;step<=8;step++) {
            double t=-half+(2*half*step/8.0);
            for(int a=-1;a<=1;a+=2) for(int b=-1;b<=1;b+=2) {
                world.spawnParticle(Particle.DUST,center.clone().add(t,a*half,b*half),1,0,0,0,0,new Particle.DustOptions(edge,1.35f));
                world.spawnParticle(Particle.DUST,center.clone().add(a*half,t,b*half),1,0,0,0,0,new Particle.DustOptions(edge,1.35f));
                world.spawnParticle(Particle.DUST,center.clone().add(a*half,b*half,t),1,0,0,0,0,new Particle.DustOptions(edge,1.35f));
            }
        }
        world.spawnParticle(Particle.REVERSE_PORTAL,center,22,0.42,0.42,0.42,0.08);
    }

    // Короткие 3D-сценки из настоящих BlockDisplay: это объекты мира, а не плоские частицы.
    private void spawnVoxelScene(String key, Location origin) {
        World world=origin.getWorld(); if(world==null || !world.isChunkLoaded(origin.getBlockX()>>4,origin.getBlockZ()>>4)) return;
        Material[] palette=switch(key) {
            case "blackhole" -> new Material[]{Material.CRYING_OBSIDIAN,Material.PURPLE_STAINED_GLASS,Material.AMETHYST_BLOCK,Material.OBSIDIAN};
            case "frost" -> new Material[]{Material.PACKED_ICE,Material.BLUE_ICE,Material.ICE,Material.LIGHT_BLUE_STAINED_GLASS};
            case "godslayer" -> new Material[]{Material.GOLD_BLOCK,Material.GILDED_BLACKSTONE,Material.QUARTZ_BLOCK,Material.YELLOW_STAINED_GLASS};
            case "worldeater" -> new Material[]{Material.OBSIDIAN,Material.CRYING_OBSIDIAN,Material.PURPLE_STAINED_GLASS,Material.AMETHYST_BLOCK};
            case "venom" -> new Material[]{Material.EMERALD_BLOCK,Material.SLIME_BLOCK,Material.LIME_STAINED_GLASS,Material.MOSS_BLOCK};
            case "vampirism","bloodsucker" -> new Material[]{Material.REDSTONE_BLOCK,Material.REDSTONE_LAMP,Material.RED_STAINED_GLASS,Material.NETHER_WART_BLOCK};
            case "thunder" -> new Material[]{Material.LIGHTNING_ROD,Material.COPPER_BLOCK,Material.GOLD_BLOCK,Material.YELLOW_STAINED_GLASS};
            case "execution","berserk" -> new Material[]{Material.RED_NETHER_BRICKS,Material.REDSTONE_BLOCK,Material.NETHERITE_BLOCK,Material.RED_STAINED_GLASS};
            case "reflection","titanshield","unbreakable" -> new Material[]{Material.IRON_BLOCK,Material.CYAN_STAINED_GLASS,Material.PRISMARINE_BRICKS,Material.DIAMOND_BLOCK};
            case "secondwind" -> new Material[]{Material.SEA_LANTERN,Material.EMERALD_BLOCK,Material.LIGHT_BLUE_STAINED_GLASS,Material.GLOWSTONE};
            case "immortality","lightaura" -> new Material[]{Material.GOLD_BLOCK,Material.SEA_LANTERN,Material.QUARTZ_BLOCK,Material.YELLOW_STAINED_GLASS};
            case "phantomblade","shadow" -> new Material[]{Material.CRYING_OBSIDIAN,Material.BLACKSTONE,Material.PURPLE_STAINED_GLASS,Material.OBSIDIAN};
            default -> new Material[]{Material.AMETHYST_BLOCK,Material.PURPLE_STAINED_GLASS,Material.QUARTZ_BLOCK,Material.CRYING_OBSIDIAN};
        };
        final List<BlockDisplay> parts=new ArrayList<>();
        final Location base=origin.clone().add(0,0.15,0);
        final int count=key.equals("blackhole")||key.equals("worldeater")?9:7;
        for(int i=0;i<count;i++) {
            final int index=i;
            Location at=base.clone().add(0,0.8,0);
            BlockDisplay display=world.spawn(at,BlockDisplay.class,entity->{
                entity.setBlock(palette[iSafe(index,palette.length)].createBlockData());
                entity.setPersistent(false);
                entity.setInvulnerable(true);
                entity.setGravity(false);
                entity.setShadowRadius(0.0f);
                entity.setViewRange(0.8f);
                float size=(index==0?0.58f:0.27f);
                entity.setTransformation(new org.bukkit.util.Transformation(
                    new org.joml.Vector3f(-size/2f,-size/2f,-size/2f),
                    new org.joml.AxisAngle4f(0,0,1,0),
                    new org.joml.Vector3f(size,size,size),
                    new org.joml.AxisAngle4f(0,0,1,0)));
            });
            parts.add(display);
        }
        new BukkitRunnable() {
            int tick=0;
            @Override public void run() {
                if(tick>=18 || !origin.isWorldLoaded()) { parts.forEach(Entity::remove); cancel(); return; }
                double progress=tick/17.0;
                double spin=tick*0.22;
                for(int i=0;i<parts.size();i++) {
                    BlockDisplay d=parts.get(i);
                    if(!d.isValid()) continue;
                    Location pos=base.clone();
                    double scale;
                    if(i==0) {
                        pos.add(0,0.85+Math.sin(tick*0.34)*0.08,0);
                        scale=(float)(0.72*(1.0-progress*0.42));
                    } else if(key.equals("blackhole")||key.equals("worldeater")) {
                        // Восемь кубов образуют вращающуюся объёмную рамку.
                        int corner=i-1;
                        double x=(corner&1)==0?-1:1, y=(corner&2)==0?-1:1, z=(corner&4)==0?-1:1;
                        double a=spin*(key.equals("blackhole")?1.0:0.72);
                        double rx=x*Math.cos(a)-z*Math.sin(a), rz=x*Math.sin(a)+z*Math.cos(a);
                        double radius=0.82*(1.0-progress*0.30);
                        pos.add(rx*radius,y*radius+0.85,rz*radius);
                        scale=(float)(0.34*(1.0-progress*0.35));
                    } else {
                        double a=spin+i*Math.PI*2.0/(parts.size()-1);
                        double radius=0.62*(1.0-progress*0.25);
                        pos.add(Math.cos(a)*radius,0.82+Math.sin(tick*0.28+i)*0.13,Math.sin(a)*radius);
                        scale=(float)(0.34*(1.0-progress*0.30));
                    }
                    d.teleport(pos);
                    d.setTransformation(new org.bukkit.util.Transformation(
                        new org.joml.Vector3f((float)(-scale/2f),(float)(-scale/2f),(float)(-scale/2f)),
                        new org.joml.AxisAngle4f((float)(spin+i*0.4),0.35f,1.0f,0.2f),
                        new org.joml.Vector3f((float)scale,(float)scale,(float)scale),
                        new org.joml.AxisAngle4f(0,0,1,0)));
                }
                if(tick%3==0) {
                    Color tint=switch(key) {
                        case "frost" -> Color.fromRGB(125,215,255);
                        case "venom" -> Color.fromRGB(80,225,95);
                        case "vampirism","bloodsucker","berserk","execution" -> Color.fromRGB(215,35,55);
                        case "godslayer","immortality","lightaura" -> Color.fromRGB(255,205,75);
                        case "reflection","titanshield","unbreakable" -> Color.fromRGB(115,225,235);
                        case "secondwind" -> Color.fromRGB(110,255,190);
                        default -> Color.fromRGB(150,70,235);
                    };
                    world.spawnParticle(Particle.DUST,base.clone().add(0,0.9,0),5,0.34,0.38,0.34,0,new Particle.DustOptions(tint,1.15f));
                }
                tick++;
            }
        }.runTaskTimer(this,0L,1L);
    }
    private int iSafe(int index,int length) { return Math.floorMod(index,length); }

    private byte entityFlags(Entity entity, boolean glowing) {
        int flags=0;
        if(entity instanceof LivingEntity burning && burning.getFireTicks()>0)flags|=0x01;
        if(entity instanceof LivingEntity living && living.isInvisible())flags|=0x20;
        if(entity instanceof Player target){
            if(target.isSneaking())flags|=0x02;
            if(target.isSprinting())flags|=0x08;
            if(target.isSwimming())flags|=0x10;
            if(target.isGliding())flags|=0x80;
        }
        if(glowing)flags|=0x40;
        return (byte)flags;
    }
    private void sendRadarGlow(Player viewer, Entity target, boolean glow) {
        if(!viewer.isOnline() || !target.isValid())return;
        byte flags=entityFlags(target,glow);
        WrapperPlayServerEntityMetadata packet=new WrapperPlayServerEntityMetadata(target.getEntityId(),List.of(new EntityData<>(0,EntityDataTypes.BYTE,flags)));
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,packet);
    }
    private void updateRadar(Player viewer) {
        UUID id=viewer.getUniqueId();
        if(!has(viewer,"radar")) {
            Integer task=radarTasks.remove(id);if(task!=null)Bukkit.getScheduler().cancelTask(task);
            Map<Integer,Entity> old=radarTargets.remove(id);if(old!=null)for(Entity entity:old.values())sendRadarGlow(viewer,entity,false);
            return;
        }
        if(radarTasks.containsKey(id))return;
        int task=Bukkit.getScheduler().scheduleSyncRepeatingTask(this,()->{
            if(!viewer.isOnline()||!has(viewer,"radar")){
                Integer active=radarTasks.remove(id);if(active!=null)Bukkit.getScheduler().cancelTask(active);
                Map<Integer,Entity> old=radarTargets.remove(id);if(old!=null)for(Entity entity:old.values())sendRadarGlow(viewer,entity,false);
                return;
            }
            Map<Integer,Entity> current=new HashMap<>();
            for(Entity entity:viewer.getNearbyEntities(20,20,20))if(entity instanceof LivingEntity && entity!=viewer){
                current.put(entity.getEntityId(),entity);sendRadarGlow(viewer,entity,true);
            }
            Map<Integer,Entity> previous=radarTargets.getOrDefault(id,Map.of());
            for(var entry:previous.entrySet())if(!current.containsKey(entry.getKey()))sendRadarGlow(viewer,entry.getValue(),false);
            radarTargets.put(id,current);
        },0L,10L);
        radarTasks.put(id,task);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onExhaustion(org.bukkit.event.entity.EntityExhaustionEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        String biome=p.getLocation().getBlock().getBiome().name();
        if(has(p,"chameleon") && (biome.contains("DESERT") || biome.contains("BADLANDS"))) { e.setCancelled(true); return; }
        ItemStack weapon = p.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType() == Material.AIR) return;
        String type = weapon.getType().name();
        if (isSpear(weapon.getType()) && hasEnchant(weapon, "spear_dash")) {
            e.setCancelled(true);
        }
    }
    private String gradient(String input, int tier) {
        int[][] colors = switch(tier) {
            case 0,1 -> new int[][]{{255,35,55},{255,105,115},{255,190,195},{255,255,255}};
            case 2 -> new int[][]{{255,135,25},{255,185,75},{255,225,165},{255,255,255}};
            case 3 -> new int[][]{{165,55,255},{195,125,255},{230,195,255},{255,255,255}};
            case 4 -> new int[][]{{35,170,255},{125,210,255},{195,235,255},{255,255,255}};
            default -> new int[][]{{45,205,105},{125,235,160},{195,250,215},{255,255,255}};
        };
        List<String> configured=getConfig().getStringList("style.gradients."+tier);
        if(configured.size()>=2) {
            List<int[]> parsed=new ArrayList<>();
            for(String raw:configured) {
                String hex=raw.startsWith("#")?raw.substring(1):raw;
                if(!hex.matches("(?i)[0-9a-f]{6}")) continue;
                parsed.add(new int[]{Integer.parseInt(hex.substring(0,2),16),Integer.parseInt(hex.substring(2,4),16),Integer.parseInt(hex.substring(4,6),16)});
            }
            if(parsed.size()>=2) colors=parsed.toArray(new int[0][]);
        }
        StringBuilder out = new StringBuilder();
        int visible = Math.max(1, input.length()-1);
        for (int i=0;i<input.length();i++) {
            double t = (double)i/visible*(colors.length-1);
            int seg = Math.min(colors.length-2,(int)t);
            double f = t-seg;
            int r=(int)(colors[seg][0]+(colors[seg+1][0]-colors[seg][0])*f);
            int g=(int)(colors[seg][1]+(colors[seg+1][1]-colors[seg][1])*f);
            int b=(int)(colors[seg][2]+(colors[seg+1][2]-colors[seg][2])*f);
            out.append(String.format("§x§%x§%x§%x§%x§%x§%x",r>>4,r&15,g>>4,g&15,b>>4,b&15)).append(input.charAt(i));
        }
        return out.toString();
    }
    private String gradient(String input) { return gradient(input,3); }
    private boolean isSpear(Material type) {
        String name=type.name();
        return name.equals("SPEAR") || name.endsWith("_SPEAR") || type==Material.TRIDENT;
    }

    private boolean supports(String key, Material type) {
        return switch(key) {
            case "magnet" -> type.name().endsWith("_PICKAXE")||type.name().endsWith("_SHOVEL")||type.name().endsWith("_AXE");
            case "moon","godslayer" -> type.name().endsWith("_SWORD");
            case "chameleon","immortality" -> type.name().endsWith("_CHESTPLATE");
            case "berserk" -> type.name().endsWith("_AXE");
            case "worldeater" -> type==Material.MACE;
            case "reflection","secondwind","unbreakable","lightaura","shove","bloodsucker","titanshield" -> type==Material.SHIELD;
            case "drill","autosmelt","veinminer","haste","experience" -> type.name().endsWith("_PICKAXE");
            case "vampirism","frost","venom","execution","phantomblade" -> type.name().endsWith("_SWORD");
            case "armorbreaker","spear_dash","hook" -> isSpear(type);
            case "woodcutter","cleave","stun","heaviness","axemaster" -> type.name().endsWith("_AXE");
            case "fish" -> type.name().endsWith("_HELMET");
            case "thunder","blackhole","bounce" -> type==Material.MACE;
            case "dragonbreath" -> type.name().endsWith("_CHESTPLATE");
            case "sneak","diver","shadow" -> type.name().endsWith("_LEGGINGS");
            case "immunity" -> type.name().endsWith("_HELMET") || type.name().endsWith("_CHESTPLATE") || type.name().endsWith("_LEGGINGS") || type.name().endsWith("_BOOTS");
            case "softsteps","lavawalker","doublejump","waterwalker" -> type.name().endsWith("_BOOTS");
            case "owl","wisdom","radar" -> type.name().endsWith("_HELMET");
            case "sniper" -> type==Material.BOW || type==Material.CROSSBOW;
            case "wings","hover","aerodynamics" -> type==Material.ELYTRA;
            default -> false;
        };
    }
    private void putCustomEnchant(ItemStack target, Spec enchant, int lvl) {
        ItemMeta meta=target.getItemMeta();
        meta.getPersistentDataContainer().set(itemEnchantKey(enchant.key()),PersistentDataType.INTEGER,lvl);
        List<String> lore=meta.hasLore()?new ArrayList<>(meta.getLore()):new ArrayList<>();
        int tier=rarityTier(enchant);
        lore.add(gradient(specName(enchant),tier)+" §7"+(enchant.leveled()?roman(lvl):"I"));
        meta.setLore(lore);
        target.setItemMeta(meta);
    }
    @EventHandler public void prepareAnvil(PrepareAnvilEvent e) {
        AnvilInventory inv=e.getInventory();
        ItemStack left=inv.getItem(0), right=inv.getItem(1);
        String leftKey=id(left), rightKey=id(right);
        boolean leftBook=left!=null && left.getType()==Material.ENCHANTED_BOOK && leftKey!=null;
        boolean rightBook=right!=null && right.getType()==Material.ENCHANTED_BOOK && rightKey!=null;
        if(!leftBook && !rightBook) return;

        if(leftBook && rightBook) {
            Spec a=spec(leftKey), b=spec(rightKey);
            if(a==null || b==null || !leftKey.equals(rightKey) || !a.leveled() ||
               level(left)!=level(right) || level(left)>=maxLevel(a)) { e.setResult(null); return; }
            e.setResult(book(a,level(left)+1));
            inv.setRepairCost(4+level(left));
            return;
        }

        ItemStack enchantedBook=leftBook?left:right;
        ItemStack target=leftBook?right:left;
        String key=id(enchantedBook);
        Spec enchant=spec(key);
        if(target==null || target.getType()==Material.AIR || enchant==null ||
           !supports(key,target.getType()) || !canCombine(target,key) ||
           (target.getType()==Material.ELYTRA && (hasEnchant(target,"wings") || key.equals("wings") && !enchantKeys(target).isEmpty()))) {
            e.setResult(null);
            return;
        }
        ItemStack result=target.clone();
        putCustomEnchant(result,enchant,level(enchantedBook));
        e.setResult(result);
        inv.setRepairCost(3);
    }
    private Spec weightedSpec(boolean trade) {
        List<Spec> pool=specs.stream().filter(x->isEnabled(x.key()) && rarityTier(x)>0 && dropChance(x)>0).toList();
        if(pool.isEmpty()) return null;
        double total=pool.stream().mapToDouble(this::dropChance).sum();
        double roll=Math.random()*total;
        for(Spec candidate:pool) { roll-=dropChance(candidate); if(roll<=0) return candidate; }
        return pool.get(pool.size()-1);
    }
    private int tradeLevel(Spec chosen) {
        if(!chosen.leveled() || maxLevel(chosen)<=1) return 1;
        if(Math.random()<0.55) return 1;
        return 2+new Random().nextInt(maxLevel(chosen)-1);
    }
    @EventHandler public void villagerTrade(org.bukkit.event.entity.VillagerAcquireTradeEvent e) {
        if(!getConfig().getBoolean("drops.villager-trade.enabled",true)) return;
        if(Math.random()>getConfig().getDouble("drops.villager-trade.chance",35.0)/100.0) return;
        Spec chosen=weightedSpec(true);
        if(chosen==null) return;
        int lvl=tradeLevel(chosen);
        int emeraldPrice=Math.min(64,8+(lvl-1)*8+new Random().nextInt(5));
        org.bukkit.inventory.MerchantRecipe recipe=new org.bukkit.inventory.MerchantRecipe(book(chosen,lvl),12);
        recipe.addIngredient(new ItemStack(Material.EMERALD,emeraldPrice));
        e.setRecipe(recipe);
    }
    @EventHandler(priority=EventPriority.HIGH) public void villagerDeath(org.bukkit.event.entity.EntityDeathEvent e) {
        if(!getConfig().getBoolean("drops.villager-death.enabled",true)) return;
        if(!(e.getEntity() instanceof Villager)) return;
        Spec chosen=weightedSpec(false);
        if(chosen==null) return;
        // Guaranteed: every villager death gives exactly one custom book.
        int lvl=chosen.leveled()?1+new Random().nextInt(maxLevel(chosen)):1;
        ItemStack reward=book(chosen,lvl);
        e.getDrops().add(reward);
        if(rarityTier(chosen)==1 && getConfig().getBoolean("messages.divine-broadcast.enabled",true)) {
            String message=getConfig().getString("messages.divine-broadcast.format","§d✦ §f{player} нашёл §c[{enchant}]§f!")
                .replace("{player}",e.getEntity().getName()).replace("{enchant}",specName(chosen));
            for(Player online:Bukkit.getOnlinePlayers()) online.sendMessage(message);
        }
        getConfig().set("drop-count."+chosen.key(),getConfig().getInt("drop-count."+chosen.key(),0)+1);
        saveConfig();
        getLogger().info("Villager dropped custom enchantment book: "+chosen.key()+" level "+lvl);
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onDungeonLoot(LootGenerateEvent e) {
        if(!getConfig().getBoolean("drops.dungeon-chests.enabled",true)) return;
        String table=e.getLootTable().getKey().toString();
        List<String> allowed=getConfig().getStringList("drops.dungeon-chests.allowed-loot-tables");
        if(!allowed.isEmpty() && !allowed.contains(table)) return;
        double chance=Math.max(0,Math.min(100,getConfig().getDouble("drops.dungeon-chests.chance",12.0)));
        if(Math.random()*100.0>=chance) return;
        Spec chosen=weightedSpec(false);
        if(chosen==null) return;
        int lvl=chosen.leveled()?1+new Random().nextInt(maxLevel(chosen)):1;
        e.getLoot().add(book(chosen,lvl));
    }

    @EventHandler public void onMine(BlockBreakEvent e) {
        Player p=e.getPlayer(); ItemStack tool=held(p); Set<String> enchants=enchantKeys(tool);
        if(enchants.isEmpty()) return;
        if(enchants.contains("haste")) p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,100,enchantLevel(tool,"haste")-1,true,false));
        if(enchants.contains("magnet")){visual(p,"magnet",e.getBlock().getLocation().add(.5,.5,.5));Bukkit.getScheduler().runTask(this,()->{
            for(Entity ent:p.getNearbyEntities(6,6,6)) if(ent instanceof Item drop) {
                ItemStack stack=drop.getItemStack().clone();
                Map<Integer,ItemStack> rest=p.getInventory().addItem(stack);
                if(rest.isEmpty()) drop.remove(); else drop.setItemStack(rest.values().iterator().next());
            }
        });}
        for(String key:List.of("autosmelt","drill","veinminer","haste","experience")) if(enchants.contains(key)&&ready(p,key,2500))visual(p,key,e.getBlock().getLocation().add(.5,.5,.5));
        if(enchants.contains("autosmelt")) {
            Collection<ItemStack> drops=e.getBlock().getDrops(tool,p);
            List<ItemStack> cooked=new ArrayList<>();
            for(ItemStack d:drops) {
                Recipe recipe=Bukkit.getRecipesFor(new ItemStack(d.getType())).stream().filter(r->r instanceof FurnaceRecipe || r instanceof BlastingRecipe).findFirst().orElse(null);
                if(recipe instanceof CookingRecipe<?> cr) cooked.add(cr.getResult().asQuantity(d.getAmount())); else cooked.add(d);
            }
            e.setDropItems(false); Bukkit.getScheduler().runTask(this,()->cooked.forEach(d->p.getWorld().dropItemNaturally(e.getBlock().getLocation(),d)));
        }
        if(enchants.contains("drill") && drilling.add(p.getUniqueId())) {
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
        if(enchants.contains("woodcutter")&&e.getBlock().getType().name().endsWith("_LOG")&&treeBreaking.add(p.getUniqueId())){Block origin=e.getBlock();Bukkit.getScheduler().runTask(this,()->{try{Queue<Block>q=new ArrayDeque<>();Set<Block>seen=new HashSet<>();q.add(origin);seen.add(origin);int broken=0;while(!q.isEmpty()&&broken<128){Block b=q.poll();if(b!=origin&&b.getType().name().endsWith("_LOG")){if(b.breakNaturally(tool))broken++;}for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){if(x==0&&y==0&&z==0)continue;Block n=b.getRelative(x,y,z);if(seen.add(n)&&n.getType().name().endsWith("_LOG"))q.add(n);}}if(broken>0)visual(p,"woodcutter",origin.getLocation().add(.5,.5,.5));}finally{treeBreaking.remove(p.getUniqueId());}});}
        if(enchants.contains("veinminer")) {
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

    @EventHandler(priority=EventPriority.HIGH)
    public void onShieldBlock(EntityDamageByEntityEvent e) {
        if(!(e.getEntity() instanceof Player defender) || !defender.isBlocking()) return;
        ItemStack shield=defender.getInventory().getItemInOffHand();
        if(shield.getType()!=Material.SHIELD) shield=defender.getInventory().getItemInMainHand();
        if(shield.getType()!=Material.SHIELD) return;
        Set<String> ench=enchantKeys(shield); if(ench.isEmpty()) return;
        if(ench.contains("titanshield") && ready(defender,"titanshield",configuredCooldown("titanshield",8000L))) { e.setCancelled(true); visual(defender,"titanshield",defender.getLocation()); return; }
        if(!(e.getDamager() instanceof LivingEntity attacker)) return;
        if(ench.contains("reflection")) { attacker.damage(e.getDamage()*0.15,defender); visual(defender,"reflection",attacker.getLocation()); }
        if(ench.contains("unbreakable") && e.getDamager() instanceof Player axeUser && axeUser.getInventory().getItemInMainHand().getType().name().endsWith("_AXE")) shieldAxeHits.merge(defender.getUniqueId(),1,Integer::sum);
        if(ench.contains("secondwind") && defender.getHealth()<=6.0 && ready(defender,"secondwind",30000L)) { defender.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,60,1,true,true)); visual(defender,"secondwind",defender.getLocation()); }
        if(ench.contains("bloodsucker") && ready(defender,"bloodsucker",configuredCooldown("bloodsucker",15000L))) { int lvl=enchantLevel(shield,"bloodsucker"); defender.setHealth(Math.min(defender.getMaxHealth(),defender.getHealth()+lvl)); visual(defender,"bloodsucker",defender.getLocation()); }
        if(ench.contains("shove") && ready(defender,"shove",configuredCooldown("shove",20000L))) { int lvl=enchantLevel(shield,"shove"); Vector knock=attacker.getLocation().toVector().subtract(defender.getLocation().toVector()).normalize().multiply(2.0+lvl); knock.setY(0.35); attacker.setVelocity(knock); visual(defender,"shove",attacker.getLocation()); }
        if(ench.contains("lightaura") && ready(defender,"lightaura",configuredCooldown("lightaura-level-"+roman(enchantLevel(shield,"lightaura")),Math.max(96000L,120000L-12000L*(enchantLevel(shield,"lightaura")-1))))) {
            for(Entity near:defender.getNearbyEntities(5,5,5)) if(near instanceof LivingEntity victim && victim!=defender) {
                long duration=20L*(1+enchantLevel(shield,"lightaura"));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,(int)duration,0,true,true));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON,(int)duration,0,true,true)); victim.damage(2.0,defender);
            }
            visual(defender,"lightaura",defender.getLocation());
        }
    }

    @EventHandler public void onHit(EntityDamageByEntityEvent e) {
        if(!(e.getDamager() instanceof Player p)) return;
        ItemStack weapon=held(p); Set<String> keys=enchantKeys(weapon); if(keys.isEmpty())return;
        UUID uid=p.getUniqueId();
        if(keys.contains("moon")) { long t=p.getWorld().getTime(); e.setDamage(e.getDamage()*((t>=13000 && t<=23000)?1.30:0.70)); if(ready(p,"moon-visual",1500L)) visual(p,"moon",p.getLocation()); }
        if(keys.contains("godslayer") && e.getEntity() instanceof Player) { e.setDamage(e.getDamage()*2.0); visual(p,"godslayer",e.getEntity().getLocation()); }
        if(keys.contains("worldeater") && weapon.getType()==Material.MACE && p.getFallDistance()>=2.0f) {
            double raw=e.getDamage()*2.0;
            if(e.getEntity() instanceof LivingEntity victim) {
                var armor=victim.getAttribute(org.bukkit.attribute.Attribute.ARMOR);
                var toughness=victim.getAttribute(org.bukkit.attribute.Attribute.ARMOR_TOUGHNESS);
                if(armor!=null) {
                    double a=armor.getValue(), tgh=toughness==null?0:toughness.getValue();
                    double reduction=Math.min(20.0,Math.max(a/5.0,a-raw/(2.0+tgh/4.0)))/25.0;
                    raw=raw/Math.max(0.01,1.0-reduction);
                }
            }
            e.setDamage(raw); p.setHealth(Math.min(p.getMaxHealth(),p.getHealth()+4.0)); visual(p,"worldeater",p.getLocation());
        }
        if(keys.contains("execution") && e.getEntity() instanceof LivingEntity le && le.getHealth()<=le.getMaxHealth()*0.30 && ready(p,"execution",5000)) { e.setDamage(e.getDamage()*2); visual(p,"execution",le.getLocation()); }
        int hit=hitCounters.merge(uid,1,Integer::sum);
        if(keys.contains("vampirism")&&hit%5==0&&ready(p,"vampirism",5000)){p.setHealth(Math.min(p.getMaxHealth(),p.getHealth()+enchantLevel(weapon,"vampirism")));if(e.getEntity() instanceof LivingEntity bloodTarget) { spawnBloodDrips(bloodTarget); visual(p,"vampirism",bloodTarget.getLocation()); } else visual(p,"vampirism",p.getLocation());}
        if(e.getEntity() instanceof LivingEntity target) {
            if(keys.contains("vampirism") && keys.contains("venom") && hit%5==0 && ready(p,"synergy_blood_poison",15000L)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON,60,1,true,true));
                p.setHealth(Math.min(p.getMaxHealth(),p.getHealth()+1.0));
                spawnBloodDrips(target);
                visual(p,"vampirism",target.getLocation());
                p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize("§5✦ §dСинергия: §cКровавый яд §8• §fВампиризм + Веном"));
            }
            if(keys.contains("frost")&&hit%3==0&&ready(p,"frost",4000)){target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,40,0));visual(p,"frost",target.getLocation());}
            if(keys.contains("venom")&&hit%3==0&&ready(p,"venom",5000)){target.addPotionEffect(new PotionEffect(PotionEffectType.POISON,100,0));visual(p,"venom",target.getLocation());}
            if(keys.contains("thunder") && Math.random()<0.20 && ready(p,"thunder",7000)) {
                target.getWorld().strikeLightning(target.getLocation()); target.damage(15.0,p); visual(p,"thunder",target.getLocation());
            }
            if(keys.contains("blackhole") && weapon.getType()==Material.MACE) {
                String pair=uid+":"+target.getUniqueId();
                int hits=maceHitCounters.merge(pair,1,Integer::sum);
                if(hits%2==0&&ready(p,"blackhole",6000)){immobilize(target);visual(p,"blackhole",target.getLocation());}
            }
            if(keys.contains("armorbreaker")&&isSpear(weapon.getType())) {
                int lvl=enchantLevel(weapon,"armorbreaker");double ignore=switch(lvl){case 1->0.10;case 2->0.15;default->0.25;};
                var armor=target.getAttribute(org.bukkit.attribute.Attribute.ARMOR);
                if(armor!=null){double reduction=Math.min(0.80,armor.getValue()*0.04);e.setDamage(e.getDamage()/(1.0-reduction*ignore));}
            }
            if(weapon.getType().name().endsWith("_AXE")) {
                if(keys.contains("cleave") && Math.random()<0.15 && ready(p,"cleave",5000)) { target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,100,0,true,true)); visual(p,"cleave",target.getLocation()); }
                if(keys.contains("stun") && hit%3==0 && ready(p,"stun",8000)) { immobilize(target,30L); visual(p,"stun",target.getLocation()); }
                if(keys.contains("heaviness") && hit%2==0 && ready(p,"heaviness",3000)) { target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,20,1,true,true)); visual(p,"heaviness",target.getLocation()); }
                if(keys.contains("axemaster")) { int lv=enchantLevel(weapon,"axemaster"); double ignore=.05*lv; var arm=target.getAttribute(org.bukkit.attribute.Attribute.ARMOR); if(arm!=null){double reduction=Math.min(.80,arm.getValue()*.04);e.setDamage(e.getDamage()/(1.0-reduction*ignore));} if(ready(p,"axemaster",2500))visual(p,"axemaster",target.getLocation()); }
            }
            if(keys.contains("phantomblade")&&ready(p,"phantomblade",7000)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,100,0,true,false,false));visual(p,"phantomblade",p.getLocation());
                p.getWorld().spawnParticle(Particle.SMOKE,p.getLocation().add(0,1,0),18,0.35,0.5,0.35,0.02);
            }
        }
    }

    @EventHandler public void onProjectileDamage(EntityDamageByEntityEvent e) {
        if(!(e.getDamager() instanceof Projectile projectile) || !(projectile.getShooter() instanceof Player shooter)) return;
        ItemStack bow=null;
        for(ItemStack item:shooter.getInventory().getContents()) if(item!=null && (item.getType()==Material.BOW || item.getType()==Material.CROSSBOW) && hasEnchant(item,"sniper")) { bow=item; break; }
        if(bow==null) return;
        int level=enchantLevel(bow,"sniper"); e.setDamage(e.getDamage()*(1.0+0.10*level));
        if(ready(shooter,"sniper",2000L)) visual(shooter,"sniper",e.getEntity().getLocation());
    }
    @EventHandler public void onDamage(EntityDamageEvent e) {
        if(!(e.getEntity() instanceof Player p)) return;
        if(has(p,"immortality") && e.getFinalDamage()>=p.getHealth() && ready(p,"immortality",configuredCooldown("immortality",20000L))) {
            e.setCancelled(true); p.setHealth(2.0); p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,40,4,true,false)); visual(p,"immortality",p.getLocation()); return;
        }
        if(has(p,"immunity") && Math.random()<0.20 && ready(p,"immunity",10000)) { for(PotionEffect effect:new ArrayList<>(p.getActivePotionEffects())) if(effect.getType().equals(PotionEffectType.SLOWNESS)||effect.getType().equals(PotionEffectType.WEAKNESS)||effect.getType().equals(PotionEffectType.BLINDNESS)||effect.getType().equals(PotionEffectType.POISON)||effect.getType().equals(PotionEffectType.NAUSEA)) p.removePotionEffect(effect.getType()); visual(p,"immunity",p.getLocation()); }
        int soft=equippedLevel(p,"softsteps"); if(e.getCause()==EntityDamageEvent.DamageCause.FALL && soft>0) { e.setDamage(e.getDamage()*(1-0.30*soft)); if(ready(p,"softsteps",3000))visual(p,"softsteps",p.getLocation()); }
        if(e.getCause()==EntityDamageEvent.DamageCause.FALL && soft>=3)e.setDamage(0);
        if(e.getCause()==EntityDamageEvent.DamageCause.FLY_INTO_WALL && p.isGliding() && has(p,"aerodynamics")) { e.setCancelled(true); if(ready(p,"aerodynamics",2500))visual(p,"aerodynamics",p.getLocation()); }
    }
    @EventHandler public void attackerNausea(EntityDamageByEntityEvent e) {
        if(e.getEntity() instanceof Player victim && e.getDamager() instanceof LivingEntity attacker && has(victim,"dragonbreath") && Math.random()<0.10 && ready(victim,"dragonbreath",8000)) { attacker.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA,100,0)); visual(victim,"dragonbreath",attacker.getLocation()); }
    }
    private void refreshDiver(Player p) {
        if (!has(p,"diver") || !inWater(p)) return;
        int level = Math.max(1, Math.min(3, equippedLevel(p,"diver")));
        // Ослабление примерно в 1.6 раза: ступени усиления сжаты, максимум — эффект II.
        int amplifier = Math.min(1, (int)Math.floor((level - 1) / 1.6));
        PotionEffect current = p.getPotionEffect(PotionEffectType.DOLPHINS_GRACE);
        // Reapply periodically so the bonus remains active even when the player is swimming without triggering move events.
        if (current == null || current.getAmplifier() < amplifier || current.getDuration() < 25) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE,40,amplifier,true,false),true);
        }
        if (ready(p,"diver",6000L)) visual(p,"diver",p.getLocation());
    }

    private boolean inWater(Player p) { return p.isInWater() || p.getLocation().getBlock().getType()==Material.WATER; }
    private void applyChameleon(Player p) {
        String biome=p.getLocation().getBlock().getBiome().name();
        if(biome.contains("SNOW") || biome.contains("FROZEN") || biome.contains("ICE")) { p.setFreezeTicks(0); p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,100,0,true,false)); }
        else if(biome.contains("DESERT") || biome.contains("BADLANDS")) { p.setFoodLevel(20); p.setSaturation(20f); p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,100,0,true,false)); }
        else if(p.getWorld().getEnvironment()==World.Environment.NETHER) p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,100,0,true,false));
        else if(biome.contains("FOREST") || biome.contains("TAIGA") || biome.contains("JUNGLE")) p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,100,0,true,false));
        if(ready(p,"chameleon-visual",8000L)) visual(p,"chameleon",p.getLocation());
    }
    private void updateBerserk(Player p) {
        if(!held(p).getType().name().endsWith("_AXE")) { resetBerserk(p); return; }
        var attr=p.getAttribute(org.bukkit.attribute.Attribute.ATTACK_SPEED); if(attr==null)return;
        berserkBase.putIfAbsent(p.getUniqueId(),attr.getBaseValue());
        double hp=p.getHealth()/p.getMaxHealth();
        double bonus=hp<0.10?3.2:hp<0.40?2.0:hp<0.70?0.8:0.0;
        attr.setBaseValue(berserkBase.get(p.getUniqueId())+bonus);
    }
    private void resetBerserk(Player p) {
        var attr=p.getAttribute(org.bukkit.attribute.Attribute.ATTACK_SPEED);
        Double old=berserkBase.remove(p.getUniqueId());
        if(attr!=null && old!=null) attr.setBaseValue(old);
    }
    private boolean makeSurface(Player p, Material source, Material replacement, long ticks, boolean lava) {
        Block under=p.getLocation().subtract(0,1,0).getBlock(); if(under.getType()!=source)return false;
        int changed=0;
        int cx=under.getX(),cy=under.getY(),cz=under.getZ();
        for(int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) {
            if(x*x+z*z>5)continue;
            Block b=under.getWorld().getBlockAt(cx+x,cy,cz+z); if(b.getType()!=source)continue;
            Location loc=b.getLocation(); b.setType(replacement,false); changed++; if(lava)lavaBlocks.add(loc);
            Bukkit.getScheduler().runTaskLater(this,()->{Block now=loc.getBlock();if(now.getType()==replacement)now.setType(source,false);lavaBlocks.remove(loc);},ticks);
        }
        return changed>0;
    }

    @EventHandler public void onMove(PlayerMoveEvent e) {
        Player p=e.getPlayer();
        if(has(p,"chameleon")) applyChameleon(p);
        if(has(p,"owl")&&!p.hasPotionEffect(PotionEffectType.NIGHT_VISION)){p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,260,0,true,false));if(ready(p,"owl",10000))visual(p,"owl",p.getLocation());}
        if(has(p,"fish") && inWater(p) && !p.hasPotionEffect(PotionEffectType.WATER_BREATHING)){p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING,260,0,true,false));if(p.isInWater()&&ready(p,"fish",8000))visual(p,"fish",p.getLocation());}
        updateRadar(p);
        if(has(p,"berserk") && held(p).getType().name().endsWith("_AXE")) updateBerserk(p); else resetBerserk(p);
        if(has(p,"lavawalker") && makeSurface(p,Material.LAVA,Material.OBSIDIAN,80L,true) && ready(p,"lavawalker",2500)) visual(p,"lavawalker",p.getLocation());
        if(has(p,"waterwalker") && makeSurface(p,Material.WATER,Material.BLUE_ICE,60L,false) && ready(p,"waterwalker",2500)) visual(p,"waterwalker",p.getLocation());
        if(has(p,"sneak")) {
            var attr=p.getAttribute(org.bukkit.attribute.Attribute.SNEAKING_SPEED);
            if(attr!=null) {
                if(p.isSneaking()) { sneakSpeeds.putIfAbsent(p.getUniqueId(),attr.getBaseValue()); attr.setBaseValue(1.0); }
                else { Double old=sneakSpeeds.remove(p.getUniqueId()); if(old!=null)attr.setBaseValue(old); }
            }
        }

        if(has(p,"doublejump") && System.currentTimeMillis()>=doubleJumpCooldown.getOrDefault(p.getUniqueId(),0L) && !p.isFlying() && !p.isGliding() && !p.isOnGround()) p.setAllowFlight(true);
        else if(!p.isFlying() && !p.isGliding() && p.isOnGround() && p.getGameMode()!=GameMode.CREATIVE && p.getGameMode()!=GameMode.SPECTATOR) p.setAllowFlight(false);
        if(has(p,"hover") && p.isSneaking() && !p.isOnGround()) { p.setVelocity(new Vector(p.getVelocity().getX(),0,p.getVelocity().getZ())); p.setFallDistance(0); if(ready(p,"hover",5000))visual(p,"hover",p.getLocation()); }
        // Detect one jump per ground-to-air transition; Bukkit has no PlayerJumpEvent.
        UUID uid = p.getUniqueId();
        if (p.isOnGround()) {
            wingsJumping.remove(uid);
        } else if (!wingsJumping.contains(uid) && p.getVelocity().getY() > 0.12 && has(p,"wings")) {
            ItemStack chest = p.getInventory().getChestplate();
            if (chest != null && chest.getType() == Material.ELYTRA && chest.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable d) {
                int restore = (int) Math.ceil(chest.getType().getMaxDurability() * 0.30);
                d.setDamage(Math.max(0, d.getDamage() - restore));
                chest.setItemMeta(d);
                }
            wingsJumping.add(uid);
        }
    }
    @EventHandler public void onShadowSneak(PlayerToggleSneakEvent e) {
        Player p=e.getPlayer(); UUID id=p.getUniqueId();
        Integer old=shadowTasks.remove(id); if(old!=null)Bukkit.getScheduler().cancelTask(old);
        if(!e.isSneaking() || !has(p,"shadow")) { shadowSince.remove(id); shadowPositions.remove(id); p.removePotionEffect(PotionEffectType.INVISIBILITY); return; }
        visual(p,"shadow",p.getLocation());
        Location[] anchor={p.getLocation().clone()}; long[] stillSince={System.currentTimeMillis()};
        int task=Bukkit.getScheduler().scheduleSyncRepeatingTask(this,()->{
            if(!p.isOnline() || !p.isSneaking() || !has(p,"shadow")) {
                Integer active=shadowTasks.remove(id); if(active!=null)Bukkit.getScheduler().cancelTask(active);
                shadowSince.remove(id);shadowPositions.remove(id);p.removePotionEffect(PotionEffectType.INVISIBILITY);return;
            }
            Location now=p.getLocation();
            if(anchor[0].getWorld()!=now.getWorld() || anchor[0].distanceSquared(now)>0.0025) {
                anchor[0]=now.clone();stillSince[0]=System.currentTimeMillis();p.removePotionEffect(PotionEffectType.INVISIBILITY);
            } else if(System.currentTimeMillis()-stillSince[0]>=5000L) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,40,0,true,false));
            }
        },0L,5L);
        shadowTasks.put(id,task);
    }

    @EventHandler public void onDoubleJump(PlayerToggleFlightEvent e) {
        Player p=e.getPlayer();
        if(!has(p,"doublejump") || p.getGameMode()==GameMode.CREATIVE || p.getGameMode()==GameMode.SPECTATOR) return;
        e.setCancelled(true);
        if(System.currentTimeMillis()<doubleJumpCooldown.getOrDefault(p.getUniqueId(),0L)) return;
        if(!ready(p,"doublejump",40000L)) return;
        doubleJumpCooldown.put(p.getUniqueId(),System.currentTimeMillis()+40000L);
        p.setAllowFlight(false);Vector v=p.getVelocity();v.setY(0.72);p.setVelocity(v);visual(p,"doublejump",p.getLocation());
    }
    @EventHandler public void onShieldItemDamage(PlayerItemDamageEvent e) {
        ItemStack item=e.getItem();
        if(item.getType()!=Material.SHIELD || !hasEnchant(item,"unbreakable")) return;
        int hits=shieldAxeHits.getOrDefault(e.getPlayer().getUniqueId(),0);
        if(hits%5!=0) e.setCancelled(true); else { e.setDamage(item.getType().getMaxDurability()); shieldAxeHits.put(e.getPlayer().getUniqueId(),0); }
    }
    @EventHandler public void onBlockBreak(BlockBreakEvent e) {
        if(lavaBlocks.contains(e.getBlock().getLocation())) { e.setCancelled(true); e.setDropItems(false); }
    }
    @EventHandler public void onInteract(PlayerInteractEvent e) {
        if(e.getHand()!=EquipmentSlot.HAND) return;
        Player p=e.getPlayer(); ItemStack item=e.getItem(); if(item==null) return;
        boolean right=e.getAction()==org.bukkit.event.block.Action.RIGHT_CLICK_AIR || e.getAction()==org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;
        if(!right) return;
        if(item.getType()==Material.MACE&&hasEnchant(item,"bounce")&&ready(p,"bounce",12000)) {
            Vector v=p.getVelocity(); v.setY(2.2); p.setVelocity(v);
            visual(p,"bounce",p.getLocation());e.setCancelled(true);return;
        }
        if(isSpear(item.getType()) && hasEnchant(item,"hook") && ready(p,"hook",8000)) {
            var hit=p.getWorld().rayTraceEntities(p.getEyeLocation(),p.getEyeLocation().getDirection(),5.0,0.8,x->x instanceof LivingEntity && x!=p);
            if(hit!=null && hit.getHitEntity() instanceof LivingEntity target && p.hasLineOfSight(target)) {
                Vector pull=p.getLocation().toVector().subtract(target.getLocation().toVector()).normalize().multiply(1.05);
                pull.setY(Math.max(0.18,pull.getY()+0.15)); target.setVelocity(pull);
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON,100,0,true,true));visual(p,"hook",target.getLocation());
            } e.setCancelled(true); return;
        }
    }

    @EventHandler public void onConsume(PlayerItemConsumeEvent e) { Player p=e.getPlayer(); if(has(p,"wisdom")) { p.giveExp(1); if(ready(p,"wisdom",3000L)) visual(p,"wisdom",p.getLocation()); } }
    @EventHandler public void onExp(PlayerExpChangeEvent e) { Player p=e.getPlayer(); int lv=equippedLevel(p,"experience"); if(lv>0) { e.setAmount(e.getAmount()*(1+lv)); if(ready(p,"experience-visual",3000L)) visual(p,"experience",p.getLocation()); } if(has(p,"wisdom")) { e.setAmount(e.getAmount()*2); if(ready(p,"wisdom-visual",3000L)) visual(p,"wisdom",p.getLocation()); } }
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        UUID uid=e.getPlayer().getUniqueId();
        hitCounters.remove(uid);
        maceHitCounters.keySet().removeIf(k->k.startsWith(uid.toString()+":"));
        sneakSpeeds.remove(uid); berserkBase.remove(uid);
        doubleJumpCooldown.remove(uid);procCooldowns.keySet().removeIf(k->k.startsWith(uid.toString()+":"));procCooldownDurations.keySet().removeIf(k->k.startsWith(uid.toString()+":"));
        shadowSince.remove(uid); shadowPositions.remove(uid);
        Integer shadowTask=shadowTasks.remove(uid); if(shadowTask!=null)Bukkit.getScheduler().cancelTask(shadowTask);
        Integer task=radarTasks.remove(uid); if(task!=null) Bukkit.getScheduler().cancelTask(task);
        radarTargets.remove(uid);
    }
}