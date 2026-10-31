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
 * [M16] 弹药箱合成配方（max 拍板 1：任意弹态弹药盒 + 木板 → 弹药箱，弹药随行）。
 *
 * 为什么需要自定义 Recipe（vanilla crafting_shaped 做不到的两点）：
 *   1. 产物动态：result 不是静态物品——要把输入盒内的弹药按堆转进产物箱 NBT
 *      （BlockEntityTag.Items + sf:fill，与 AmmoCrateBlockItem.writeBack 同构）。
 *   2. 盒守恒返还：tacz 弹药不可堆叠（AmmoItem stacksTo(1) 源码实证），36 格箱
 *      最多容纳 36×堆上限发——钻石盒容量（getStackSize×AMMO_BOX_STACK_SIZE×3）远超。
 *      getRemainingItems 把超出部分留在原盒返还（vanilla remainder 机制，牛奶桶同类），
 *      零弹药丢失（[M16] 对原拍板的偏差修正，max 回来可复核）。
 *
 * 实现模式：组合委托 delegate = vanilla ShapedRecipe——matches/原料/分组/静态 result
 * 全部委托（配方书 ghost 摆放、原料判定零新逻辑），只覆写 assemble（动态产物）与
 * getRemainingItems（盒返还）。网络同步（fromNetwork/toNetwork）由 Serializer
 * 全委托 vanilla ShapedRecipe.Serializer——客户端拿到的就是标准 ShapedRecipe 数据，
 * 配方书渲染零风险。
 *
 * 输入盒语义：空盒 / 创造盒 / 无效弹药 ID → 空箱（不转移）；普通盒按
 * min(盒内数, 箱容量) 转移，超出留在返还盒里。创造盒当空盒处理且原样返还
 * （创造物品不参与经济——凭空复制弹药进箱是漏洞）。
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
        return delegate.matches(inv, level);
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

    // ---------- 覆写段：动态产物 + 盒守恒 ----------

    /**
     * 动态产物：读输入盒弹药 → 分堆填 36 格 → 产物箱 NBT。
     * 纯函数（不改输入）：预览（配方书）多次调用安全。
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
        // 堆上限动态取（tacz AmmoItem stacksTo(1) 实证，但按 API 取防数据包例外）
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
     * 盒守恒返还（vanilla remainder 机制）：合成取走产物后，输入盒留在原格。
     * 空盒/创造盒 → 原样返还；普通盒 → 返还「扣掉已转数」的盒（掏空后 AmmoId 归 empty，
     * Level 标签保留——仍匹配 D 键 {Level:2}，木板够时可链式合成）。
     */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer inv) {
        NonNullList<ItemStack> remainders = NonNullList.withSize(inv.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof IAmmoBox iBox) {
                ItemStack back = s.copy();
                if (!iBox.isCreative(s) && !iBox.isAllTypeCreative(s)) {
                    int left = iBox.getAmmoCount(s) - transferableCount(s);
                    iBox.setAmmoCount(back, Math.max(left, 0));
                    if (left <= 0) {
                        iBox.setAmmoId(back, DefaultAssets.EMPTY_AMMO_ID);
                    }
                }
                remainders.set(i, back);
            }
        }
        return remainders;
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
     * 可转移数：盒内有效弹药数与箱容量（36×堆上限）的交。
     * 空盒 / 创造盒 / 弹种 ID 无效（数据包缺项）→ 0（当空箱处理）。
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