package com.lmaxroutercn.sustainedfire.crafting;

import com.google.gson.JsonObject;
import com.lmaxroutercn.sustainedfire.block.entity.AmmoCrateBlockEntity;
import com.lmaxroutercn.sustainedfire.item.AmmoCrateBlockItem;
import com.lmaxroutercn.sustainedfire.registry.ModRecipeSerializers;
import com.lmaxroutercn.sustainedfire.util.AmmoCrateInventoryHandler;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

/**
 * [①配方重拍·2026-11-01] 弹药箱合成配方（max 终板：消耗盒 + 弹药满转移 + 装不下拒绝合成）。
 *
 * 为什么需要自定义 Recipe（vanilla crafting_shaped 做不到的两点）：
 *   1. 产物动态：result 不是静态物品——要把输入盒内的弹药按堆转进产物箱 NBT
 *      （BlockEntityTag.Items + sf:fill，与 AmmoCrateBlockItem.writeBack 同构）。
 *   2. 门检动态：满转移语义要求「盒内弹药整箱装得下」才放行——容量 =
 *      CONTAINER_SIZE 格 × 弹堆上限，两数全部运行时取（max 关切的
 *      「配置/数据包改容量」天然免疫，partial_nbt 表达不了这种联动判定）。
 *      装不下 → matches false：合成根本不发生（vanilla UI 表现为不出产物），
 *      零截断零丢失，优于任何静默部分转移方案。
 *
 * 材料语义（[①重拍]）：弹药盒是消耗性材料（8 木板 + 钻石盒 → 箱）。不消耗盒
 * = 无限复印机（一个盒 + N×8 木板 = N 个供弹设施，Minecraft 容器方块合成
 * 无「用完退还」先例正是此因），故 getRemainingItems 显式全空返回，不走
 * vanilla 默认链路（默认会询问 item 级 remainder——tacz AmmoBoxItem 覆写有
 * 疑似 hasCraftingRemainingItem 的 m_5812_，返回值未实证，显式覆写使消耗语义自持）。
 *
 * 实现模式：组合委托 delegate = vanilla ShapedRecipe——pattern/原料/分组/静态
 * result 全部委托（配方书 ghost 摆放、原料判定零新逻辑），只覆写 matches
 * （加门检）、assemble（动态产物）与 getRemainingItems（消耗盒）。网络同步
 * （fromNetwork/toNetwork）由 Serializer 全委托 vanilla ShapedRecipe.Serializer
 * ——客户端拿到的就是标准 ShapedRecipe 数据，配方书渲染零风险。
 *
 * 输入盒语义：空盒 → 空箱（盒照常消耗）；普通盒 → 弹药全额入箱；盒内数超
 * 箱容量 / 创造盒 / 无效弹种 ID → 拒绝合成（matches false）。创造盒拒绝理由：
 * 盒已是消耗品，放行不可获取的创造物品参与经济无意义，且堵死潜在复印路径。
 */
public class AmmoCrateRecipe implements CraftingRecipe {
    /** vanilla 委托：pattern 解析/匹配/原料列表/静态产物（空箱）全走它 */
    private final ShapedRecipe delegate;

    public AmmoCrateRecipe(ShapedRecipe delegate) {
        this.delegate = delegate;
    }

    // ---------- 委托段：行为与 vanilla shaped 完全一致 ----------

    @Override
    public boolean matches(CraftingContainer inv, Level level) {
        if (!delegate.matches(inv, level)) {
            return false; // 图案/原料判定委托 vanilla，不过直接否
        }
        ItemStack box = findAmmoBox(inv);
        if (box == null) {
            return true; // 防御：pattern 已匹配却找不到盒（正常不可达），走 assemble 空箱兜底
        }
        IAmmoBox iBox = (IAmmoBox) box.getItem();
        // [①重拍] 创造盒不可作材料（理由见类注释）
        if (iBox.isCreative(box) || iBox.isAllTypeCreative(box)) {
            return false;
        }
        // [①重拍] 满转移门检：transferableCount 内含容量截断，其返回值等于盒内
        // 原始数 即「整箱装得下」。堆上限/槽数全部运行时取，无静态容量假设。
        return transferableCount(box) == iBox.getAmmoCount(box);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return delegate.canCraftInDimensions(width, height);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return delegate.getIngredients();
    }

    @Override
    public String getGroup() {
        return delegate.getGroup();
    }

    /** [M16 修正] 1.20.1 接口方法名是 category()（无 get 前缀；getCategory() 是 1.20.5+ 改名） */
    @Override
    public CraftingBookCategory category() {
        return delegate.category();
    }

    /** 配方 id 委托（Recipe 接口 1.20.1 抽象方法） */
    @Override
    public ResourceLocation getId() {
        return delegate.getId();
    }

    @Override
    public boolean isSpecial() {
        return delegate.isSpecial();
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        // 静态产物（配方书图标 / ghost 预览）：delegate 的 result = JSON 里的空箱
        return delegate.getResultItem(registryAccess);
    }

    // ---------- 覆写段：动态产物 + 盒消耗 ----------

    /**
     * 动态产物：读输入盒弹药 → 分堆填满箱格 → 产物箱 NBT。
     * 纯函数（不改输入）：预览（配方书）多次调用安全。
     * [①重拍] matches 门检已保证盒内数不超箱容量；transferableCount 的 min
     * 截断保留为非 vanilla 调用路径（直接调 assemble 不经 matches）的防御。
     */
    @Override
    public ItemStack assemble(CraftingContainer inv, RegistryAccess registryAccess) {
        ItemStack result = delegate.getResultItem(registryAccess).copy();
        ItemStack box = findAmmoBox(inv);
        if (box == null) {
            return result; // 防御：matches 已保证 pattern，正常路径不可达
        }
        int transfer = transferableCount(box);
        if (transfer <= 0) {
            return result; // 空盒/创造盒/无效弹种 → 空箱
        }
        IAmmoBox iBox = (IAmmoBox) box.getItem();
        ResourceLocation ammoId = iBox.getAmmoId(box);
        // 堆上限动态取：弹种数据包可改堆叠，API 实时查询，不设静态假设
        int maxPerStack = AmmoItemBuilder.create().setId(ammoId).setCount(1).build().getMaxStackSize();
        // 分堆填箱：序列化与 BE/物品侧同构（FixedCapacityItemStackHandler 语义）
        AmmoCrateInventoryHandler handler = new AmmoCrateInventoryHandler(AmmoCrateBlockEntity.CONTAINER_SIZE);
        int remaining = transfer;
        int stacks = 0;
        for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
            int n = Math.min(maxPerStack, remaining);
            handler.setStackInSlot(slot, AmmoItemBuilder.create().setId(ammoId).setCount(n).build());
            remaining -= n;
            stacks++;
        }
        // 产物 NBT：BlockEntityTag{Items}（放置时 vanilla 自动恢复）+ sf:fill（耐久条/模型谓词 O(1) 读）
        result.getOrCreateTagElement("BlockEntityTag").put("Items", handler.serializeNBT());
        result.getOrCreateTag().putInt(AmmoCrateBlockItem.TAG_FILL, stacks);
        return result;
    }

    /**
     * [①重拍] 盒为消耗性材料：显式全空返回（所有输入含弹药盒一并消耗）。
     * 不删此覆写走 vanilla 默认的原因：默认实现会询问 item 级 remainder
     * （tacz AmmoBoxItem 覆写有疑似 hasCraftingRemainingItem 的 m_5812_，
     * 返回值未实证）——若其真返盒则复印机复活；显式覆写使消耗语义不依赖
     * 任何上游行为。
     */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer inv) {
        return NonNullList.withSize(inv.getContainerSize(), ItemStack.EMPTY);
    }

    /** 扫容器找弹药盒（IAmmoBox 实例判定，不依赖 pattern 位置——JSON 改图案零影响） */
    private static ItemStack findAmmoBox(CraftingContainer inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof IAmmoBox) {
                return s;
            }
        }
        return null;
    }

    /**
     * 可转移数：盒内有效弹药数与箱容量（CONTAINER_SIZE×堆上限）的交。
     * 空盒 / 创造盒 / 弹种 ID 无效（数据包缺项）→ 0。
     * [①重拍] 兼任 matches 门检真值源：返回值 == 盒内原始数 即整箱装得下。
     */
    private static int transferableCount(ItemStack box) {
        IAmmoBox iBox = (IAmmoBox) box.getItem();
        if (iBox.isCreative(box) || iBox.isAllTypeCreative(box)) {
            return 0;
        }
        ResourceLocation ammoId = iBox.getAmmoId(box);
        if (ammoId.equals(DefaultAssets.EMPTY_AMMO_ID)) {
            return 0;
        }
        // 弹种有效性：无效 ID 的 AmmoItemBuilder 产物未定义，先验注册表挡掉
        if (TimelessAPI.getCommonAmmoIndex(ammoId).isEmpty()) {
            return 0;
        }
        int count = iBox.getAmmoCount(box);
        if (count <= 0) {
            return 0;
        }
        int maxPerStack = AmmoItemBuilder.create().setId(ammoId).setCount(1).build().getMaxStackSize();
        return Math.min(count, AmmoCrateBlockEntity.CONTAINER_SIZE * maxPerStack);
    }

    // ---------- 序列化 ----------

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.AMMO_CRATE_RECIPE.get();
    }

    @Override
    public RecipeType<?> getType() {
        return RecipeType.CRAFTING;
    }

    /**
     * 全委托 vanilla ShapedRecipe.Serializer：fromJson / fromNetwork 解析标准 shaped
     * 数据后包一层 AmmoCrateRecipe；toNetwork 只发 delegate——两端 serializer 同为
     * sustainedfire:ammo_crate，客户端解析回来还是 AmmoCrateRecipe，双向闭环。
     */
    public static class Serializer implements RecipeSerializer<AmmoCrateRecipe> {
        private final ShapedRecipe.Serializer delegate = new ShapedRecipe.Serializer();

        @Override
        public AmmoCrateRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new AmmoCrateRecipe(delegate.fromJson(id, json));
        }

        @Override
        public AmmoCrateRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            return new AmmoCrateRecipe(delegate.fromNetwork(id, buf));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, AmmoCrateRecipe recipe) {
            delegate.toNetwork(buf, recipe.delegate);
        }
    }
}