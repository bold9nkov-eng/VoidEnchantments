package ru.voidmain.enchants;

import org.bukkit.*;
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
    private final Set<UUID> drilling = new HashSet<>();
    private final Map<UUID, Integer> hitCounters = new HashMap<>();
    private final Map<UUID, Long> hoverTicks = new HashMap<>();
    private final Map<UUID, Integer> radarTasks = new HashMap<>();
    private final Map<String, Integer> maceHitCounters = new HashMap<>();
    private final Map<UUID, Float> sneakSpeeds = new HashMap<>();
    private final Set<UUID> wingsJumping = new HashSet<>();
    private final Map<String, Integer> villagerBookDrops = new HashMap<>();
    private static final String ADMIN_TITLE = "§0✦ §5VoidMain §8| §cАдмин-панель";
    private static final String CATALOG_TITLE = "§0✦ §5VoidMain §8| §dЗачарования";
    private static final String LEVEL_TITLE = "§0✦ §5VoidMain §8| §dВыбор уровня";

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
        new Spec("thunder","Громовой удар","Молния наносит в 4 раза больше урона",Material.MACE,1,false),
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
        new Spec("spear_dash","Неутомимый рывок","Рывок копьём не расходует голод",Material.TRIDENT,1,false)
    );
    private final Set<String> singleTier = new HashSet<>();

    @Override public void onEnable() {
        enchantKey = new NamespacedKey(this,"custom_enchant");
        levelKey = new NamespacedKey(this,"custom_level");
        for (Spec s : specs) if (!s.leveled()) singleTier.add(s.key());
        getServer().getPluginManager().registerEvents(this,this);
        Objects.requireNonNull(getCommand("voidenchants")).setExecutor(this);
        Objects.requireNonNull(getCommand("voidenchants")).setTabCompleter(this);
        getConfig().options().copyDefaults(true);
        saveConfig();
        getLogger().info("VoidEnchantments enabled: "+specs.size()+" enchantments");
    }

    private Spec spec(String key) { return specs.stream().filter(s -> s.key().equalsIgnoreCase(key)).findFirst().orElse(null); }
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
    private int rarityTier(Spec s) { return switch(s.key()) {
        case "thunder" -> 1;
        case "blackhole","spear_dash" -> 5;
        case "phantomblade","aerodynamics" -> 3;
        case "radar" -> 4;
        case "wings","execution" -> 1;
        case "drill","vampirism","sniper" -> 2;
        case "veinminer","hover","lavawalker","dragonbreath" -> 3;
        case "magnet","autosmelt","experience","frost","venom","immunity","softsteps" -> 4;
        default -> 5;
    }; }
    private String rarityName(int t) { return switch(t) {
        case 1 -> "Божественное"; case 2 -> "Легендарное"; case 3 -> "Эпическое"; case 4 -> "Редкое"; default -> "Необычное";
    }; }
    private String rarityColor(int t) { return switch(t) {
        case 1 -> "§c"; case 2 -> "§6"; case 3 -> "§5"; case 4 -> "§b"; default -> "§a";
    }; }
    private String appliesTo(Spec s) { return switch(s.key()) {
        case "magnet","drill","autosmelt","veinminer","haste","experience" -> "Кирка";
        case "vampirism","frost","venom","execution" -> "Меч";
        case "thunder","blackhole" -> "Булава";
        case "phantomblade" -> "Меч";
        case "radar" -> "Шлем";
        case "aerodynamics" -> "Элитры";
        case "spear_dash" -> "Копьё"; case "dragonbreath" -> "Нагрудник";
        case "sneak","diver" -> "Поножи"; case "immunity" -> "Любая броня";
        case "softsteps","lavawalker" -> "Ботинки"; case "owl","wisdom" -> "Шлем";
        case "sniper" -> "Лук или арбалет"; case "wings","hover" -> "Элитры";
        default -> "Неизвестно";
    }; }
    private ItemStack book(Spec s,int lvl) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta m = item.getItemMeta();
        int tier=rarityTier(s);
        m.setDisplayName(gradient(s.name(),tier));
        m.setLore(List.of("§7"+s.lore(),"","§7Предмет: §f"+appliesTo(s),
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
    private double defaultDropChance(Spec s) { return switch(rarityTier(s)) { case 1 -> 0.05; case 2 -> 0.15; case 3 -> 0.5; case 4 -> 1.5; default -> 4.0; }; }
    private double dropChance(Spec s) { return getConfig().getDouble("drop-chance."+s.key(), defaultDropChance(s)); }
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
        try { return Math.max(1,Math.min(s.max(),Integer.parseInt(raw))); }
        catch(Exception ignored) { return 1; }
    }
    private void giveBook(Player p,Spec s,int lvl) {
        Map<Integer,ItemStack> rest=p.getInventory().addItem(book(s,lvl));
        rest.values().forEach(it->p.getWorld().dropItemNaturally(p.getLocation(),it));
    }
    private void sendSpecInfo(CommandSender sender,Spec s) {
        int tier=rarityTier(s);
        sender.sendMessage("§5✦ §l"+s.name()+" §8("+s.key()+")");
        sender.sendMessage("§7Описание: §f"+s.lore());
        sender.sendMessage("§7Предмет: §f"+appliesTo(s));
        sender.sendMessage("§7Редкость: "+rarityColor(tier)+rarityName(tier));
        sender.sendMessage("§7Уровни: §f"+(s.leveled()?"I–"+roman(s.max()):"без уровней"));
        sender.sendMessage("§7Статус: "+(isEnabled(s.key())?"§aвключено":"§cотключено"));
    }
    private boolean removeEnchant(Player p,Spec s) {
        boolean changed=false;
        for(ItemStack item:p.getInventory().getContents()) changed|=removeEnchantFromItem(item,s);
        for(ItemStack item:p.getInventory().getArmorContents()) changed|=removeEnchantFromItem(item,s);
        changed|=removeEnchantFromItem(p.getInventory().getItemInOffHand(),s);
        p.updateInventory();
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
        lore.removeIf(line->ChatColor.stripColor(line).startsWith(s.name()));
        meta.setLore(lore.isEmpty()?null:lore);
        item.setItemMeta(meta);
        return true;
    }
    private void openAdmin(Player p) {
        Inventory inv=Bukkit.createInventory(null,54,ADMIN_TITLE);
        for(int i=0;i<specs.size() && i<45;i++) {
            Spec s=specs.get(i); int tier=rarityTier(s);
            ItemStack icon=new ItemStack(s.icon()); ItemMeta m=icon.getItemMeta();
            m.setDisplayName(gradient(s.name(),tier));
            m.setLore(List.of("§7"+s.lore(),"§7Предмет: §f"+appliesTo(s),
                "§7Редкость: "+rarityColor(tier)+rarityName(tier),
                "§7Эффект: "+(isEnabled(s.key())?"§aВКЛЮЧЁН":"§cВЫКЛЮЧЕН"),
                "§7Шанс с жителя: §f"+String.format(Locale.US,"%.3f",dropChance(s))+"%",
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
            if(sender instanceof Player p) openCatalog(p,0); else sendHelp(sender,admin);
            return true;
        }
        if(sub.equals("help")) { sendHelp(sender,admin); return true; }
        if(sub.equals("list")) {
            sender.sendMessage("§5§lVoidMain §8| §dКастомные зачарования:");
            for(Spec s:specs) sender.sendMessage(rarityColor(rarityTier(s))+"• "+s.name()+" §8— §7"+s.key()+" §8| §f"+appliesTo(s));
            return true;
        }
        if(sub.equals("info") || sub.equals("инфо")) {
            if(args.length<2) { sender.sendMessage("§cИспользование: /enchants info <чар>"); return true; }
            Spec s=findSpec(args[1]); if(s==null) sender.sendMessage("§cТакое зачарование не найдено."); else sendSpecInfo(sender,s);
            return true;
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
            case "give" -> {
                if(args.length<3) { sender.sendMessage("§c/ve give <игрок> <чар> [уровень]"); return true; }
                Player target=Bukkit.getPlayerExact(args[1]); Spec s=findSpec(args[2]);
                if(target==null || s==null) { sender.sendMessage("§cИгрок или зачарование не найдено."); return true; }
                int lvl=args.length>3?parseLevel(s,args[3]):1; giveBook(target,s,lvl);
                sender.sendMessage(gradient("Выдано игроку "+target.getName()+": "+s.name()+" "+roman(lvl)));
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
                    sender.sendMessage(rarityColor(rarityTier(s))+s.name()+" §8— §f"+count+" §7шт. §8| §7шанс "+String.format(Locale.US,"%.3f",dropChance(s))+"%");
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
        List<String> roots=new ArrayList<>(List.of("help","list","info","apply"));
        if(admin) roots.addAll(List.of("give","giveall","remove","book","reload","debug","drops","admin"));
        if(args.length==1) return roots.stream().filter(x->x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        String sub=args[0].toLowerCase(Locale.ROOT);
        if(!admin && !List.of("info","apply","list","help").contains(sub)) return List.of();
        if(args.length==2 && List.of("give","remove","debug").contains(sub) && admin)
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(x->x.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        if((args.length==2 && List.of("info","giveall","book").contains(sub)) || (args.length==3 && List.of("give","remove").contains(sub))) {
            return specs.stream().map(Spec::key).filter(x->x.startsWith(args[args.length-1].toLowerCase(Locale.ROOT))).toList();
        }
        if((args.length==4 && sub.equals("give")) || (args.length==3 && List.of("giveall","book").contains(sub))) return List.of("1","2","3","4");
        return List.of();
    }
    private void openCatalog(Player p,int page) {
        Inventory inv=Bukkit.createInventory(null,54,CATALOG_TITLE);
        int start=page*45;
        for(int i=0;i<45 && start+i<specs.size();i++) {
            Spec s=specs.get(start+i);
            ItemStack icon=new ItemStack(s.icon()); ItemMeta m=icon.getItemMeta();
            int tier=rarityTier(s);
            m.setDisplayName(rarityColor(tier)+s.name());
            m.setLore(List.of("§7"+s.lore(),"","§7Предмет: §f"+appliesTo(s),
                "§7Редкость: "+rarityColor(tier)+rarityName(tier),
                "§7Уровни: §f"+(s.leveled()?"I–"+roman(s.max()):"Без уровней"),
                "",s.leveled()?"§eНажми, чтобы выбрать уровень":"§eНажми, чтобы получить книгу"));
            m.getPersistentDataContainer().set(enchantKey,PersistentDataType.STRING,s.key());
            icon.setItemMeta(m); inv.setItem(i,icon);
        }
        ItemStack close=new ItemStack(Material.BARRIER); ItemMeta cm=close.getItemMeta();
        cm.setDisplayName("§cЗакрыть"); close.setItemMeta(cm); inv.setItem(49,close);
        p.openInventory(inv);
    }
    private void openLevelMenu(Player p,Spec s) {
        Inventory inv=Bukkit.createInventory(null,27,LEVEL_TITLE);
        int tier=rarityTier(s);
        for(int lvl=1;lvl<=s.max();lvl++) {
            ItemStack icon=new ItemStack(Material.ENCHANTED_BOOK); ItemMeta meta=icon.getItemMeta();
            meta.setDisplayName(gradient(s.name(),tier)+" §7"+roman(lvl));
            meta.setLore(List.of("§7"+s.lore(),"","§7Предмет: §f"+appliesTo(s),
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
                p.sendMessage("§dШанс "+selected.name()+": §f"+String.format(Locale.US,"%.3f",next)+"%");
            } else {
                List<String> disabled=new ArrayList<>(getConfig().getStringList("disabled-enchants"));
                if(disabled.contains(selected.key())) disabled.remove(selected.key()); else disabled.add(selected.key());
                getConfig().set("disabled-enchants",disabled); saveConfig();
                p.sendMessage("§d"+selected.name()+": "+(isEnabled(selected.key())?"§aвключено":"§cотключено"));
            }
            openAdmin(p); return;
        }
        if(title.equals(LEVEL_TITLE)) {
            e.setCancelled(true);
            if(e.getRawSlot()<0 || e.getRawSlot()>=27) return;
            ItemStack clicked=e.getCurrentItem();
            if(clicked==null || clicked.getType()==Material.AIR) return;
            if(e.getRawSlot()==26) { p.closeInventory(); return; }
            if(e.getRawSlot()==18) { openCatalog(p,0); return; }
            Spec selected=findSpec(id(clicked));
            if(selected==null || !selected.leveled()) return;
            int lvl=level(clicked);
            if(lvl<1 || lvl>selected.max()) return;
            giveBook(p,selected,lvl);
            p.playSound(p.getLocation(),Sound.UI_BUTTON_CLICK,0.8f,1.4f);
            p.sendMessage(gradient("✦ Получена книга: "+selected.name()+" "+roman(lvl)));
            p.closeInventory(); return;
        }
        if(!title.equals(CATALOG_TITLE)) return;
        e.setCancelled(true);
        if(e.getRawSlot()<0 || e.getRawSlot()>=54) return;
        ItemStack clicked=e.getCurrentItem();
        if(clicked==null || clicked.getType()==Material.AIR) return;
        if(e.getRawSlot()==49 && clicked.getType()==Material.BARRIER) { p.closeInventory(); return; }
        Spec selected=findSpec(id(clicked));
        if(selected==null) return;
        if(selected.leveled()) { openLevelMenu(p,selected); return; }
        giveBook(p,selected,1);
        p.playSound(p.getLocation(),Sound.UI_BUTTON_CLICK,0.8f,1.5f);
        p.sendMessage(gradient("✦ Получена книга: "+selected.name()));
        p.closeInventory();
    }

    private void immobilize(LivingEntity target) {
        if(target instanceof Player victim) { float old=victim.getWalkSpeed(); victim.setWalkSpeed(0f); victim.setVelocity(new Vector(0,0,0)); Bukkit.getScheduler().runTaskLater(this,()->{if(victim.isOnline())victim.setWalkSpeed(old);},40L); }
        else if(target instanceof Mob mob) { boolean old=mob.hasAI(); mob.setAI(false); Bukkit.getScheduler().runTaskLater(this,()->{if(mob.isValid())mob.setAI(old);},40L); }
        else { target.setVelocity(new Vector(0,0,0)); target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,40,10,true,false)); }
        target.getWorld().spawnParticle(Particle.PORTAL,target.getLocation().add(0,1,0),35,0.5,0.7,0.5,0.15);
    }
    private void updateRadar(Player viewer) {
        UUID id=viewer.getUniqueId();
        if(!has(viewer,"radar")) {Integer task=radarTasks.remove(id);if(task!=null)Bukkit.getScheduler().cancelTask(task);return;}
        if(radarTasks.containsKey(id))return;
        int task=Bukkit.getScheduler().scheduleSyncRepeatingTask(this,()->{if(!viewer.isOnline()||!has(viewer,"radar")){Integer active=radarTasks.remove(id);if(active!=null)Bukkit.getScheduler().cancelTask(active);return;}for(Entity entity:viewer.getNearbyEntities(20,20,20))if(entity instanceof LivingEntity && entity!=viewer)viewer.spawnParticle(Particle.END_ROD,entity.getLocation().add(0,1,0),1,0.12,0.25,0.12,0);},0L,10L);
        radarTasks.put(id,task);
    }
    @EventHandler public void onExhaustion(org.bukkit.event.entity.EntityExhaustionEvent e) { if(e.getEntity() instanceof Player p && has(p,"spear_dash")) e.setCancelled(true); }
    private String gradient(String input, int tier) {
        int[][] colors = switch(tier) {
            case 1 -> new int[][]{{255,35,55},{255,105,115},{255,190,195},{255,255,255}};
            case 2 -> new int[][]{{255,135,25},{255,185,75},{255,225,165},{255,255,255}};
            case 3 -> new int[][]{{165,55,255},{195,125,255},{230,195,255},{255,255,255}};
            case 4 -> new int[][]{{35,170,255},{125,210,255},{195,235,255},{255,255,255}};
            default -> new int[][]{{45,205,105},{125,235,160},{195,250,215},{255,255,255}};
        };
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
    private boolean supports(String key, Material type) {
        return switch(key) {
            case "magnet","drill","autosmelt","veinminer","haste","experience" -> type.name().endsWith("_PICKAXE");
            case "vampirism","frost","venom","execution","phantomblade" -> type.name().endsWith("_SWORD");
            case "thunder","blackhole" -> type==Material.MACE;
            case "dragonbreath" -> type.name().endsWith("_CHESTPLATE");
            case "sneak","diver" -> type.name().endsWith("_LEGGINGS");
            case "immunity" -> type.name().endsWith("_HELMET") || type.name().endsWith("_CHESTPLATE") || type.name().endsWith("_LEGGINGS") || type.name().endsWith("_BOOTS");
            case "softsteps","lavawalker" -> type.name().endsWith("_BOOTS");
            case "owl","wisdom","radar" -> type.name().endsWith("_HELMET");
            case "sniper" -> type==Material.BOW || type==Material.CROSSBOW;
            case "wings","hover","aerodynamics" -> type==Material.ELYTRA;
            case "spear_dash" -> type.name().equals("SPEAR") || type==Material.TRIDENT;
            default -> false;
        };
    }
    private void putCustomEnchant(ItemStack target, Spec enchant, int lvl) {
        ItemMeta meta=target.getItemMeta();
        meta.getPersistentDataContainer().set(itemEnchantKey(enchant.key()),PersistentDataType.INTEGER,lvl);
        List<String> lore=meta.hasLore()?new ArrayList<>(meta.getLore()):new ArrayList<>();
        int tier=rarityTier(enchant);
        lore.add(rarityColor(tier)+enchant.name()+" §7"+(enchant.leveled()?roman(lvl):"I"));
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
               level(left)!=level(right) || level(left)>=a.max()) { e.setResult(null); return; }
            e.setResult(book(a,level(left)+1));
            inv.setRepairCost(4+level(left));
            return;
        }

        ItemStack enchantedBook=leftBook?left:right;
        ItemStack target=leftBook?right:left;
        String key=id(enchantedBook);
        Spec enchant=spec(key);
        if(target==null || target.getType()==Material.AIR || enchant==null ||
           !supports(key,target.getType()) || hasEnchant(target,key) ||
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
        // Drops: 60/25/10/4/1. Trades: 60/30/5/3/0.5, normalized to 100%.
        // Keep selectedTier final for Java lambda capture.
        double[] weights = trade
            ? new double[]{0.5,3.0,5.0,30.0,60.0}
            : new double[]{1.0,4.0,10.0,25.0,60.0};
        double roll=Math.random()*Arrays.stream(weights).sum();
        int tier=5;
        for(int i=0;i<weights.length;i++) {
            roll-=weights[i];
            if(roll<0) { tier=i+1; break; }
        }
        final int selectedTier=tier;
        List<Spec> pool=specs.stream().filter(x -> rarityTier(x)==selectedTier && isEnabled(x.key())).toList();
        if(pool.isEmpty()) pool=specs.stream().filter(x->isEnabled(x.key())).toList();
        if(pool.isEmpty()) pool=specs;
        return pool.isEmpty()?null:pool.get(new Random().nextInt(pool.size()));
    }
    private int tradeLevel(Spec chosen) {
        if(!chosen.leveled() || chosen.max()<=1) return 1;
        if(Math.random()<0.55) return 1;
        return 2+new Random().nextInt(chosen.max()-1);
    }
    @EventHandler public void villagerTrade(org.bukkit.event.entity.VillagerAcquireTradeEvent e) {
        if(Math.random()>0.35) return;
        Spec chosen=weightedSpec(true);
        if(chosen==null) return;
        int lvl=tradeLevel(chosen);
        int emeraldPrice=Math.min(64,8+(lvl-1)*8+new Random().nextInt(5));
        org.bukkit.inventory.MerchantRecipe recipe=new org.bukkit.inventory.MerchantRecipe(book(chosen,lvl),12);
        recipe.addIngredient(new ItemStack(Material.EMERALD,emeraldPrice));
        e.setRecipe(recipe);
    }
    @EventHandler(priority=EventPriority.HIGH) public void villagerDeath(org.bukkit.event.entity.EntityDeathEvent e) {
        if(!(e.getEntity() instanceof Villager)) return;
        Spec chosen=weightedSpec(false);
        if(chosen==null) return;
        // Guaranteed: every villager death gives exactly one custom book.
        int lvl=chosen.leveled()?1+new Random().nextInt(chosen.max()):1;
        ItemStack reward=book(chosen,lvl);
        e.getDrops().add(reward);
        getConfig().set("drop-count."+chosen.key(),getConfig().getInt("drop-count."+chosen.key(),0)+1);
        saveConfig();
        getLogger().info("Villager dropped custom enchantment book: "+chosen.key()+" level "+lvl);
    }

    @EventHandler public void onMine(BlockBreakEvent e) {
        Player p=e.getPlayer(); ItemStack tool=held(p); Set<String> enchants=enchantKeys(tool);
        if(enchants.isEmpty()) return;
        if(enchants.contains("haste")) p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,100,enchantLevel(tool,"haste")-1,true,false));
        if(enchants.contains("magnet")) Bukkit.getScheduler().runTask(this,()->{
            for(Entity ent:p.getNearbyEntities(6,6,6)) if(ent instanceof Item drop) {
                ItemStack stack=drop.getItemStack().clone();
                Map<Integer,ItemStack> rest=p.getInventory().addItem(stack);
                if(rest.isEmpty()) drop.remove(); else drop.setItemStack(rest.values().iterator().next());
            }
        });
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

    @EventHandler public void onHit(EntityDamageByEntityEvent e) {
        if(!(e.getDamager() instanceof Player p)) return;
        ItemStack weapon=held(p); Set<String> keys=enchantKeys(weapon); if(keys.isEmpty())return;
        UUID uid=p.getUniqueId();
        if(keys.contains("execution") && e.getEntity() instanceof LivingEntity le && le.getHealth()<=le.getMaxHealth()*0.30) e.setDamage(e.getDamage()*2);
        int hit=hitCounters.merge(uid,1,Integer::sum);
        if(keys.contains("vampirism") && hit%5==0) p.setHealth(Math.min(p.getMaxHealth(),p.getHealth()+enchantLevel(weapon,"vampirism")));
        if(e.getEntity() instanceof LivingEntity target) {
            if(keys.contains("frost") && hit%3==0) target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,40,0));
            if(keys.contains("venom") && hit%3==0) target.addPotionEffect(new PotionEffect(PotionEffectType.POISON,100,0));
            if(keys.contains("thunder") && Math.random()<0.12) {
                target.getWorld().strikeLightning(target.getLocation());
                target.damage(15.0, p);
            }
            if(keys.contains("blackhole") && weapon.getType()==Material.MACE) {
                String pair=uid+":"+target.getUniqueId();
                int hits=maceHitCounters.merge(pair,1,Integer::sum);
                if(hits%2==0) immobilize(target);
            }
            if(keys.contains("phantomblade")) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,100,0,true,false,false));
                p.getWorld().spawnParticle(Particle.SMOKE,p.getLocation().add(0,1,0),18,0.35,0.5,0.35,0.02);
            }
        }
    }
    @EventHandler public void onDamage(EntityDamageEvent e) {
        if(!(e.getEntity() instanceof Player p)) return;
        if(has(p,"immunity") && Math.random()<0.20) for(PotionEffect effect:new ArrayList<>(p.getActivePotionEffects())) if(effect.getType().equals(PotionEffectType.SLOWNESS)||effect.getType().equals(PotionEffectType.WEAKNESS)||effect.getType().equals(PotionEffectType.BLINDNESS)||effect.getType().equals(PotionEffectType.POISON)||effect.getType().equals(PotionEffectType.NAUSEA)) p.removePotionEffect(effect.getType());
        int soft=equippedLevel(p,"softsteps"); if(e.getCause()==EntityDamageEvent.DamageCause.FALL && soft>0) e.setDamage(e.getDamage()*(1-0.30*soft));
        if(e.getCause()==EntityDamageEvent.DamageCause.FALL && soft>=3)e.setDamage(0);
        if(e.getCause()==EntityDamageEvent.DamageCause.FLY_INTO_WALL && p.isGliding() && has(p,"aerodynamics")) e.setCancelled(true);
    }
    @EventHandler public void attackerNausea(EntityDamageByEntityEvent e) {
        if(e.getEntity() instanceof Player victim && e.getDamager() instanceof LivingEntity attacker && has(victim,"dragonbreath") && Math.random()<0.10)
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA,100,0));
    }
    @EventHandler public void onMove(PlayerMoveEvent e) {
        Player p=e.getPlayer();
        if(has(p,"owl") && !p.hasPotionEffect(PotionEffectType.NIGHT_VISION)) p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,260,0,true,false));
        updateRadar(p);
        if(has(p,"lavawalker")) {
            Block under=p.getLocation().subtract(0,1,0).getBlock();
            if(under.getType()==Material.LAVA) for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){Block b=under.getRelative(x,0,z);if(b.getType()==Material.LAVA){b.setType(Material.OBSIDIAN);Location blockLoc=b.getLocation();Bukkit.getScheduler().runTaskLater(this,()->{Block current=blockLoc.getBlock();if(current.getType()==Material.OBSIDIAN)current.setType(Material.LAVA);},80L);}}
        }
        if(has(p,"sneak")) { if(p.isSneaking()) { sneakSpeeds.putIfAbsent(p.getUniqueId(),p.getWalkSpeed()); p.setWalkSpeed(0.2f); } else { Float old=sneakSpeeds.remove(p.getUniqueId()); if(old!=null && Math.abs(p.getWalkSpeed()-0.2f)<0.001f)p.setWalkSpeed(old); } }
        if(has(p,"hover") && p.isSneaking() && !p.isOnGround()) { p.setVelocity(new Vector(p.getVelocity().getX(),0,p.getVelocity().getZ())); p.setFallDistance(0); }
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
    @EventHandler public void onConsume(PlayerItemConsumeEvent e) {
        Player p=e.getPlayer(); if(has(p,"wisdom")) p.giveExp(1);
    }
    @EventHandler public void onExp(PlayerExpChangeEvent e) {
        Player p=e.getPlayer(); int lv=equippedLevel(p,"experience"); if(lv>0)e.setAmount(e.getAmount()*(1+lv));
        if(has(p,"wisdom"))e.setAmount(e.getAmount()*2);
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        UUID uid=e.getPlayer().getUniqueId();
        hitCounters.remove(uid);
        maceHitCounters.keySet().removeIf(k->k.startsWith(uid.toString()+":"));
        sneakSpeeds.remove(uid);
        Integer task=radarTasks.remove(uid);
        if(task!=null) Bukkit.getScheduler().cancelTask(task);
    }
}
