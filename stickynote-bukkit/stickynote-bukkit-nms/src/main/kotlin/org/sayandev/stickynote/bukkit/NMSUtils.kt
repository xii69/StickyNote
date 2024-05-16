package org.sayandev.stickynote.bukkit

import com.cryptomorin.xseries.ReflectionUtils
import net.kyori.adventure.platform.bukkit.MinecraftComponentSerializer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.block.BlockState
import org.bukkit.block.Sign
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.jetbrains.annotations.ApiStatus
import org.sayandev.stickynote.bukkit.utils.ServerVersion
import org.sayandev.stickynote.core.math.Vector3
import org.sayandev.stickynote.nms.accessors.*
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.channels.Channel
import java.util.*
import java.util.concurrent.Future
import java.util.function.UnaryOperator

object NMSUtils {

    private val CRAFT_ITEM_STACK: Result<Class<*>> = runCatching { ReflectionUtils.getCraftClass("inventory.CraftItemStack") }
    private val CRAFT_PLAYER: Result<Class<*>> = runCatching { ReflectionUtils.getCraftClass("entity.CraftPlayer") }
    private val CRAFT_WORLD: Result<Class<*>> = runCatching { ReflectionUtils.getCraftClass("CraftWorld") }
    private val CRAFT_SERVER: Result<Class<*>> = runCatching { ReflectionUtils.getCraftClass("CraftServer") }
    private val CRAFT_BLOCK_STATE: Result<Class<*>> = runCatching { ReflectionUtils.getCraftClass("block.CraftBlockState") }
    private val CRAFT_LIVING_ENTITY: Result<Class<*>> = runCatching { ReflectionUtils.getCraftClass("entity.CraftLivingEntity") }
    private val CRAFT_ENTITY: Result<Class<*>> = runCatching { ReflectionUtils.getCraftClass("entity.CraftEntity") }
    /**
     * mc-1.9 and above
     */
    private val CRAFT_BLOCK_ENTITY_STATE: Result<Class<*>> = runCatching { ReflectionUtils.getCraftClass("block.CraftBlockEntityState") }
    private val CRAFT_CHUNK: Result<Class<*>> = runCatching { ReflectionUtils.getCraftClass("CraftChunk") }

    private val CRAFT_ITEM_STACK_AS_NMS_COPY: Result<Method> = runCatching { CRAFT_ITEM_STACK.getOrThrow().getMethod("asNMSCopy", ItemStack::class.java) }
    private val CRAFT_ITEM_STACK_AS_BUKKIT_COPY: Result<Method> = runCatching { CRAFT_ITEM_STACK.getOrThrow().getMethod("asBukkitCopy", ItemStackAccessor.TYPE) }
    private val CRAFT_PLAYER_GET_HANDLE_METHOD: Result<Method> = runCatching { CRAFT_PLAYER.getOrThrow().getMethod("getHandle") }
    private val CRAFT_WORLD_GET_HANDLE_METHOD: Result<Method> = runCatching { CRAFT_WORLD.getOrThrow().getMethod("getHandle") }
    private val CRAFT_SERVER_GET_SERVER_METHOD: Result<Method> = runCatching { CRAFT_SERVER.getOrThrow().getMethod("getServer") }
    /**
     * mc-1.13 and above
     */
    private val CRAFT_BLOCK_STATE_GET_HANDLE_METHOD: Result<Method> = runCatching { CRAFT_BLOCK_STATE.getOrThrow().getMethod("getHandle") }
    private val CRAFT_LIVING_ENTITY_GET_HANDLE_METHOD: Result<Method> = runCatching { CRAFT_LIVING_ENTITY.getOrThrow().getMethod("getHandle") }
    private val CRAFT_ENTITY_GET_HANDLE_METHOD: Result<Method> = runCatching { CRAFT_ENTITY.getOrThrow().getMethod("getHandle") }
    private val ENTITY_GET_BUKKIT_ENTITY_METHOD: Result<Method> = runCatching { EntityAccessor.TYPE!!.getMethod("getBukkitEntity") }
    /**
     * mc-1.9 and above
     */
    private val CRAFT_BLOCK_ENTITY_STATE_GET_TITE_ENTITY_METHOD: Result<Method> = runCatching { CRAFT_BLOCK_ENTITY_STATE.getOrThrow().getDeclaredMethod("getTileEntity").apply { isAccessible = true } }
    private val CRAFT_CHUNK_GET_HANDLE_METHOD: Result<Method> = runCatching { if (ServerVersion.supports(19)) CRAFT_CHUNK.getOrThrow().getMethod("getHandle", ChunkStatusAccessor.TYPE) else CRAFT_CHUNK.getOrThrow().getMethod("getHandle") }

    /**
     * mc-1.13 and above
     */
    private val LIVING_ENTITY_DROPS_FIELD: Result<Field> = runCatching { LivingEntityAccessor.TYPE!!.getField("drops") }

    fun getNmsItemStack(item: ItemStack?): Any {
        return CRAFT_ITEM_STACK_AS_NMS_COPY.getOrThrow().invoke(null, item)
    }

    fun getNmsEmptyItemStack(): Any {
        return ItemStackAccessor.FIELD_EMPTY!!
    }

    fun getBukkitItemStack(nmsItem: Any): ItemStack {
        return CRAFT_ITEM_STACK_AS_BUKKIT_COPY.getOrThrow().invoke(null, nmsItem) as ItemStack
    }

    fun getItemStackComponent(item: ItemStack): Component {
        return MinecraftComponentSerializer.get().deserialize(
            ItemStackAccessor.METHOD_GET_DISPLAY_NAME!!.invoke(getNmsItemStack(item))
        )
    }

    fun getItemStackNBTJson(item: ItemStack): String {
        return ItemStackAccessor.METHOD_SAVE!!.invoke(
            getNmsItemStack(item),
            CompoundTagAccessor.CONSTRUCTOR_0!!.newInstance()
        ).toString()
    }

    fun getItemStackFromNBTJson(nbtJson: String?): ItemStack? {
        val compoundTag = TagParserAccessor.METHOD_PARSE_TAG!!.invoke(null, nbtJson)
        return if (ServerVersion.supports(13)) {
            getBukkitItemStack(ItemStackAccessor.METHOD_OF!!.invoke(null, compoundTag))
        } else if (ServerVersion.supports(11)) {
            getBukkitItemStack(ItemStackAccessor.CONSTRUCTOR_0!!.newInstance(compoundTag))
        } else {
            getBukkitItemStack(ItemStackAccessor.METHOD_CREATE_STACK!!.invoke(null, compoundTag))
        }
    }

    fun getItemCategory(item: ItemStack): String {
        return CreativeModeTabAccessor.FIELD_LANG_ID!!.get(
            ItemAccessor.METHOD_GET_ITEM_CATEGORY!!.invoke(
                ItemStackAccessor.METHOD_GET_ITEM!!.invoke(
                    getNmsItemStack(item)
                )
            )
        ) as String
    }

    fun setDisplayName(item: ItemStack, component: Component): ItemStack {
        //applying a temporary displayname for item to initialize its display tags
        val meta = item.itemMeta
        if (meta?.hasDisplayName() == true) {
            meta.setDisplayName("temp")
            item.setItemMeta(meta)
        }

        val nmsItem = getNmsItemStack(item)
        CompoundTagAccessor.METHOD_PUT_STRING!!.invoke(
            getItemDisplayTag(nmsItem),
            getTagDisplayName(),
            GsonComponentSerializer.gson().serialize(
                component.decoration(
                    TextDecoration.ITALIC, false
                )
            )
        )
        return getBukkitItemStack(nmsItem)
    }

    fun setLore(item: ItemStack, lines: List<Component>): ItemStack {
        //applying a sample lore for item to initialize its display tags (there's no difference in adding displayname or lore, but anyway)
        val meta = item.itemMeta
        if (meta?.hasLore() == true) {
            meta.lore = listOf("temp")
            item.setItemMeta(meta)
        }

        val nmsItem = getNmsItemStack(item)
        val stringTagList: MutableList<Any> = ArrayList()
        for (line in lines) {
            stringTagList.add(
                StringTagAccessor.CONSTRUCTOR_0!!.newInstance(
                    GsonComponentSerializer.gson().serialize(
                        line.decoration(
                            TextDecoration.ITALIC, false
                        )
                    )
                )
            )
        }
        CompoundTagAccessor.METHOD_PUT!!.invoke(
            NMSUtils.getItemDisplayTag(nmsItem),
            NMSUtils.getTagLore(),
            ListTagAccessor.CONSTRUCTOR_0!!.newInstance(stringTagList, NMSUtils.getTagString())
        )
        return getBukkitItemStack(nmsItem)
    }

    private fun getTagDisplay(): String {
        return if (ServerVersion.supports(17)) {
            ItemStackAccessor.FIELD_TAG_DISPLAY!! as String
        } else {
            "display"
        }
    }

    private fun getTagDisplayName(): String {
        return if (ServerVersion.supports(17)) {
            ItemStackAccessor.FIELD_TAG_DISPLAY_NAME!! as String
        } else {
            "Name"
        }
    }

    private fun getTagLore(): String {
        return if (ServerVersion.supports(17)) {
            ItemStackAccessor.FIELD_TAG_LORE!! as String
        } else {
            "Lore"
        }
    }

    private fun getTagString(): Byte {
        if (ServerVersion.supports(17)) {
            return TagAccessor.FIELD_TAG_STRING!! as Byte
        }
        return 8
    }

    private fun getItemDisplayTag(nmsItem: Any): Any? {
        return CompoundTagAccessor.METHOD_GET_COMPOUND!!.invoke(
            ItemStackAccessor.METHOD_GET_TAG!!.invoke(nmsItem),
            getTagDisplay()
        )
    }

    fun setPlayerCamera(player: Player, entity: Entity) {
        setPlayerCamera(player, getNmsEntity(entity))
    }

    fun setPlayerCamera(player: Player) {
        setPlayerCamera(player, player)
    }

    fun setPlayerCamera(player: Player, nmsEntity: Any) {
        ServerPlayerAccessor.METHOD_SET_CAMERA!!.invoke(getServerPlayer(player!!), nmsEntity)
    }

    fun getPlayerUseItem(player: Player): ItemStack? {
        val useItem: Any? = LivingEntityAccessor.METHOD_GET_USE_ITEM!!.invoke(getServerPlayer(player))
        if (useItem == null || useItem == getNmsEmptyItemStack()) return null
        return getBukkitItemStack(useItem)
    }

    fun getPotion(potion: ItemStack): Any {
        return PotionUtilsAccessor.METHOD_GET_POTION!!.invoke(null, getNmsItemStack(potion))
    }

    fun getPotionColor(potion: ItemStack): Int {
        return PotionUtilsAccessor.METHOD_GET_COLOR!!.invoke(null, getNmsItemStack(potion)) as Int
    }

    fun getNmsLivingEntity(livingEntity: LivingEntity): Any {
        return CRAFT_LIVING_ENTITY_GET_HANDLE_METHOD.getOrThrow().invoke(livingEntity)
    }

    /**
     * @apiNote >= 1.13
     * @param livingEntity The bukkit living entity
     * @return List of bukkit itemstack
     */
    @Suppress("unchecked_cast")
    fun getLivingEntityDrops(livingEntity: LivingEntity?): List<ItemStack> {
        return LIVING_ENTITY_DROPS_FIELD.getOrThrow().get(getNmsLivingEntity(livingEntity!!)) as List<ItemStack>
    }

    fun getNmsEntity(entity: Entity): Any {
        return CRAFT_ENTITY_GET_HANDLE_METHOD.getOrThrow().invoke(entity)
    }

    fun getBukkitEntity(nmsEntity: Any): Entity {
        return ENTITY_GET_BUKKIT_ENTITY_METHOD.getOrThrow().invoke(nmsEntity) as Entity
    }

    fun setEntityCustomName(entity: Entity, component: Component) {
        EntityAccessor.METHOD_SET_CUSTOM_NAME!!.invoke(
            getNmsEntity(entity),
            MinecraftComponentSerializer.get().serialize(component)
        )
    }

    fun getEntityDataAccessorId(entityDataAccessor: Any): Int {
        if (entityDataAccessor.javaClass != EntityDataAccessorAccessor.TYPE!!) return -1
        return EntityDataAccessorAccessor.METHOD_GET_ID!!.invoke(entityDataAccessor) as Int
    }

    @Suppress("unchecked_cast")
    fun setSignLine(sign: Sign, isFront: Boolean, line: Int, component: Component) {
        val finalLine = if (ServerVersion.supports(20)) line else line + 1
        val nmsComponent = MinecraftComponentSerializer.get().serialize(component)
        val nmsSign: Any = getNmsSign(sign)

        if (ServerVersion.supports(20)) {
            val result = SignBlockEntityAccessor.METHOD_UPDATE_TEXT!!.invoke(
                nmsSign,
                UnaryOperator { signText: Any ->
                    val updatedText = SignTextAccessor.METHOD_SET_MESSAGE!!.invoke(
                        signText,
                        finalLine,
                        MinecraftComponentSerializer.get()
                            .serialize(component)
                    )
                    updatedText
                } as UnaryOperator<Any>,
                isFront
            )
        } else if (ServerVersion.supports(13)) {
            SignBlockEntityAccessor.METHOD_SET_MESSAGE_1!!.invoke(nmsSign, finalLine - 1, nmsComponent)
        } else {
            val lines: Array<Any> = SignBlockEntityAccessor.FIELD_MESSAGES!!.get(nmsSign) as Array<Any>
            lines[finalLine - 1] = nmsComponent
            SignBlockEntityAccessor.FIELD_MESSAGES!!.set(nmsSign, lines)
        }
    }

    fun setSignLine(sign: Sign, line: Int, component: Component) {
        setSignLine(sign, true, line, component)
    }

    /**
     * @apiNote >= 1.20
     */
    fun setSignGlowing(sign: Sign, isFront: Boolean, glowing: Boolean) {
        if (!ServerVersion.supports(20)) return
        val nmsSign: Any = getNmsSign(sign)

        SignBlockEntityAccessor.METHOD_UPDATE_TEXT!!.invoke(
            nmsSign,
            UnaryOperator<Any?> { signText: Any? ->
                SignTextAccessor.METHOD_SET_HAS_GLOWING_TEXT!!.invoke(
                    signText,
                    glowing
                )
                signText
            },
            isFront
        )
    }

    /**
     * @apiNote >= 1.20
     */
    fun setSignColor(sign: Sign, isFront: Boolean, color: DyeColor) {
        if (!ServerVersion.supports(20)) return
        val nmsSign: Any = getNmsSign(sign)

        SignBlockEntityAccessor.METHOD_UPDATE_TEXT!!.invoke(
            nmsSign,
            UnaryOperator<Any?> { signText: Any? ->
                SignTextAccessor.METHOD_SET_COLOR!!.invoke(
                    signText,
                    (DyeColorAccessor::class.java.getMethod("getField" + color.name).invoke(null) as Field)[null]
                )
                signText
            },
            isFront
        )
    }

    @Suppress("unchecked_cast")
    fun getSignLine(sign: Sign, line: Int): Component {
        return MinecraftComponentSerializer.get().deserialize(
            (SignBlockEntityAccessor.FIELD_MESSAGES!!.get(getNmsSign(sign)) as Array<Any>)[line - 1]
        )
    }

    @Suppress("unchecked_cast")
    fun getSignLines(sign: Sign): List<Component> {
        val list: MutableList<Component> = ArrayList()
        for (nmsComponent in SignBlockEntityAccessor.FIELD_MESSAGES!!.get(getNmsSign(sign)) as Array<Any>) {
            list.add(MinecraftComponentSerializer.get().deserialize(nmsComponent))
        }
        return list
    }

    fun updateSign(sign: Sign) {
        if (ServerVersion.supports(17)) {
            SignBlockEntityAccessor.METHOD_MARK_UPDATED!!.invoke(getNmsSign(sign))
        } else {
            sendPacket(
                sign.block.location.world!!.players,
                SignBlockEntityAccessor.METHOD_GET_UPDATE_PACKET!!.invoke(getNmsSign(sign))
            )
        }
    }

    /**
     * @apiNote >= 1.13
     */
    fun getNmsSign(sign: Sign): Any {
        return CRAFT_BLOCK_ENTITY_STATE_GET_TITE_ENTITY_METHOD.getOrThrow().invoke(sign)
    }

    fun getNmsBlock(block: Block): Any? {
        return if (ServerVersion.supports(16)) {
            BlockBehaviour_BlockStateBaseAccessor.METHOD_GET_BLOCK!!.invoke(
                LevelAccessor.METHOD_GET_BLOCK_STATE!!.invoke(
                    getServerLevel(block.world),
                    BlockPosAccessor.CONSTRUCTOR_0!!.newInstance(block.x, block.y, block.z)
                )
            )
        } else if (ServerVersion.supports(9)) {
            BlockStateAccessor.METHOD_GET_BLOCK!!.invoke(
                LevelAccessor.METHOD_FUNC_175703_C!!.invoke(
                    getServerLevel(block.world),
                    BlockPosAccessor.CONSTRUCTOR_0!!.newInstance(block.x, block.y, block.z)
                )
            )
        } else {
            LevelAccessor.METHOD_FUNC_175703_C!!.invoke(
                getServerLevel(block.world),
                BlockPosAccessor.CONSTRUCTOR_0!!.newInstance(block.x, block.y, block.z)
            )
        }
    }

    fun getPing(player: Player): Int {
        return getPing(getServerPlayer(player))
    }

    fun getPing(serverPlayer: Any): Int {
        return if ((ServerVersion.supports(20) && ServerVersion.patchNumber() >= 2) || ServerVersion.supports(21)) {
            ServerCommonPacketListenerImplAccessor.METHOD_LATENCY!!.invoke(
                ServerPlayerAccessor.FIELD_CONNECTION!!.get(serverPlayer)
            ) as Int
        } else {
            ServerPlayerAccessor.FIELD_LATENCY!!.get(serverPlayer) as Int
        }
    }

    fun setBodyArrows(player: Player, amount: Int) {
        LivingEntityAccessor.METHOD_SET_ARROW_COUNT!!.invoke(getServerPlayer(player), amount)
    }

    fun getBodyArrows(player: Player): Int {
        return LivingEntityAccessor.METHOD_GET_ARROW_COUNT!!.invoke(getServerPlayer(player)) as Int
    }

    fun playSound(player: Player, soundEvent: Any, volume: Float, pitch: Float) {
        require(soundEvent.javaClass == SoundEventAccessor.TYPE!!) { "Sound must be a SoundEvent object" }
        PlayerAccessor.METHOD_PLAY_SOUND!!.invoke(getServerPlayer(player), soundEvent, volume, pitch)
    }

    fun getServerPlayer(player: Player): Any {
        return CRAFT_PLAYER_GET_HANDLE_METHOD.getOrThrow().invoke(player)
    }

    fun getServerLevel(world: World): Any {
        return CRAFT_WORLD_GET_HANDLE_METHOD.getOrThrow().invoke(world)
    }

    fun getLevelChunk(chunk: Chunk): Any {
        return if (ServerVersion.supports(20)) {
            CRAFT_CHUNK_GET_HANDLE_METHOD.getOrThrow().invoke(chunk, ChunkStatusAccessor.FIELD_FULL!!)
        } else {
            CRAFT_CHUNK_GET_HANDLE_METHOD.getOrThrow().invoke(chunk)
        }
    }

    fun getLevelChunk(chunk: Chunk, chunkStatus: String): Any {
        return if (ServerVersion.supports(20)) {
            CRAFT_CHUNK_GET_HANDLE_METHOD.getOrThrow().invoke(
                chunk,
                ChunkStatusAccessor.METHOD_BY_NAME!!.invoke(null, chunkStatus)
            )
        } else {
            CRAFT_CHUNK_GET_HANDLE_METHOD.getOrThrow().invoke(chunk)
        }
    }

    fun getLightEngine(world: World): Any {
        return LevelAccessor.METHOD_GET_LIGHT_ENGINE!!.invoke(getServerLevel(world))
    }

    fun getDedicatedServer(): Any {
        return CRAFT_SERVER_GET_SERVER_METHOD.getOrThrow().invoke(Bukkit.getServer())
    }

    fun getBlockState(material: Material): Any {
        return BlockAccessor.METHOD_DEFAULT_BLOCK_STATE!!.invoke(
            BlocksAccessor.TYPE!!.getField(
                material.toString().uppercase()
            ).get(null)
        )
    }

    fun getBlockState(blockState: BlockState): Any {
        return CRAFT_BLOCK_STATE_GET_HANDLE_METHOD.getOrThrow().invoke(null, blockState)
    }

    /**
     * @apiNote >= 1.9, For 1.8 use [me.mohamad82.ruom.utils.SoundGroupUtils.getBlockSound]
     */
    /*TODO SoundGroupUtils
    fun getSoundGroup(material: Material?): SoundGroup? {
        try {
            return SoundGroup(
                SoundGroupUtils.getBlockSound(SoundGroupUtils.SoundType.BREAK, material),
                SoundGroupUtils.getBlockSound(SoundGroupUtils.SoundType.STEP, material),
                SoundGroupUtils.getBlockSound(SoundGroupUtils.SoundType.PLACE, material),
                SoundGroupUtils.getBlockSound(SoundGroupUtils.SoundType.HIT, material),
                SoundGroupUtils.getBlockSound(SoundGroupUtils.SoundType.FALL, material)
            )
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * @apiNote >= 1.8
     */
    public static SoundGroup getSoundGroup(Block block) {
        try {
            return new SoundGroup(
                    SoundGroupUtils.getBlockSound(SoundGroupUtils.SoundType.BREAK, block),
                    SoundGroupUtils.getBlockSound(SoundGroupUtils.SoundType.STEP, block),
                    SoundGroupUtils.getBlockSound(SoundGroupUtils.SoundType.PLACE, block),
                    SoundGroupUtils.getBlockSound(SoundGroupUtils.SoundType.HIT, block),
                    SoundGroupUtils.getBlockSound(SoundGroupUtils.SoundType.FALL, block)
            );
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }*/

    fun getServerGamePacketListener(player: Player): Any {
        return ServerPlayerAccessor.FIELD_CONNECTION!![getServerPlayer(player)]
    }

    fun getConnection(player: Player): Any {
        val packetListener = getServerGamePacketListener(player)
        return if ((ServerVersion.supports(20) && ServerVersion.patchNumber() >= 2) || ServerVersion.supports(21)) {
            ServerCommonPacketListenerImplAccessor.FIELD_CONNECTION!!.get(packetListener)
        } else {
            ServerGamePacketListenerImplAccessor.FIELD_CONNECTION!!.get(packetListener)
        }
    }

    fun getAverageReceivedPackets(player: Player): Float {
        return ConnectionAccessor.METHOD_GET_AVERAGE_RECEIVED_PACKETS!!.invoke(getConnection(player)) as Float
    }

    fun getAverageSentPackets(player: Player): Float {
        return ConnectionAccessor.METHOD_GET_AVERAGE_SENT_PACKETS!!.invoke(getConnection(player)) as Float
    }

    fun createResourceLocation(string: String): Any {
        return ResourceLocationAccessor.CONSTRUCTOR_0!!.newInstance(string)
    }

    fun createResourceLocation(key: String, value: String): Any {
        return ResourceLocationAccessor.CONSTRUCTOR_1!!.newInstance(key, value)
    }

    fun getEntityDataSerializer(any: Any): Any? {
        return when (any.javaClass.simpleName) {
            "Byte" -> EntityDataSerializersAccessor.FIELD_BYTE!!
            "Integer" -> EntityDataSerializersAccessor.FIELD_INT!!
            "Float" -> EntityDataSerializersAccessor.FIELD_FLOAT!!
            "String" -> EntityDataSerializersAccessor.FIELD_STRING!!
            "Optional" -> EntityDataSerializersAccessor.FIELD_OPTIONAL_COMPONENT!!
            "ItemStack" -> EntityDataSerializersAccessor.FIELD_ITEM_STACK!!
            "Boolean" -> EntityDataSerializersAccessor.FIELD_BOOLEAN!!
            else -> {
                when (any.javaClass) {
                    ComponentAccessor.TYPE!! -> EntityDataSerializersAccessor.FIELD_COMPONENT!!
                    PoseAccessor.TYPE!! -> EntityDataSerializersAccessor.FIELD_POSE!!
                    else -> null
                }
            }
        }
    }

    /**
     * Returns the player's netty channel.
     * @param player The player.
     * @return The channel of the player.
     */
    fun getChannel(player: Player): Channel {
        return ConnectionAccessor.FIELD_CHANNEL!!.get(getConnection(player)) as Channel
    }

    /**
     * Disconnects (kicks) a player from the server.
     * @param player The player that is going to get disconnected.
     * @param component The component that player is going to see in their screen.
     */
    fun disconnect(player: Player, component: Component) {
        ConnectionAccessor.METHOD_DISCONNECT!!.invoke(
            getConnection(player), MinecraftComponentSerializer.get().serialize(component)
        )
    }

    /**
     * Connects a player to a server over Internet.
     * @param player The player that is going to be transfered.
     * @param inetSocketAddress The address of destination server.
     * @param flag Declear that if socket channel should be EpollSocketChannel or NioSocketChannel.
     * @return The created connection in the new server.
     */
    @ApiStatus.Experimental
    fun connectToServer(player: Player, inetSocketAddress: InetSocketAddress, flag: Boolean): Any {
        return ConnectionAccessor.METHOD_CONNECT_TO_SERVER!!.invoke(
            getConnection(player),
            inetSocketAddress,
            flag
        )
    }

    /**
     * Connects to player to a local server.
     * @param player The player that is going to be transfered.
     * @param socketAddress The address of the local destination server.
     * @return The created connection in the new server.
     */
    @ApiStatus.Experimental
    fun connectToLocalServer(player: Player, socketAddress: SocketAddress): Any {
        return ConnectionAccessor.METHOD_CONNECT_TO_LOCAL_SERVER!!.invoke(
            getConnection(player),
            socketAddress
        )
    }

    /**
     * Sends a BlockDesturctionPacket to the target location.
     * @param viewers The viewers that are going to see the change.
     * @param location The location of the block.
     * @param stage The destruction stage between 1 and 9. Any number below 1 and above 9 will remove the destruction process.
     */
    fun sendBlockDestruction(viewers: Set<Player>, location: Vector3, stage: Int) {
        sendPacket(
            viewers,
            PacketUtils.getBlockDestructionPacket(location, stage)
        )
    }

    /**
     * Sets passengers of an entity with packets.
     * @param viewers The viewers that are going to see the change.
     * @param entity The entity.
     * @param passengers The passengers that are going to ride on the entity.
     */
    fun setPassengers(viewers: Set<Player>, entity: Any, vararg passengers: Int) {
        sendPacket(
            viewers,
            PacketUtils.getEntityPassengersPacket(entity, *passengers)
        )
    }

    /**
     * Sends a ClientboundBlockEventPacket to show a chest opening or closing animation.
     * @param viewers The viewers that are going to see the change.
     * @param blockLocation The block's location.
     * @param blockMaterial The block's material. It can be chest, trapped_chest, ender_chest or shulker_box. Other values will be ignored.
     * @param open Declear that chest should get opened or closed.
     */
    fun sendChestAnimation(viewers: Set<Player>, blockLocation: Vector3, blockMaterial: Material, open: Boolean) {
        val chestAnimationPacket = PacketUtils.getBlockEventPacket(
            blockLocation,
            blockMaterial,
            1,
            if (open) 1 else 0
        )

        sendPacket(viewers, chestAnimationPacket)
    }

    fun createConnection(): Any {
        return ConnectionAccessor.CONSTRUCTOR_0!!.newInstance(PacketFlowAccessor.FIELD_SERVERBOUND!!)
    }

    /**
     * Sends one or more packets to a player.
     * @param player The player that is going to receive the packet(s).
     * @param packets The packet(s) that are going to be sent to the player.
     */
    @JvmStatic
    fun sendPacketSync(player: Player, vararg packets: Any) {
        try {
            //ReflectionUtils.sendPacketSync(player, packets);
            val commonGameConnection = getServerGamePacketListener(player)
            for (packet in packets) {
                if ((ServerVersion.supports(20) && ServerVersion.patchNumber() >= 2) || ServerVersion.supports(21)) {
                    ServerCommonPacketListenerImplAccessor.METHOD_SEND!!.invoke(commonGameConnection, packet)
                } else {
                    ServerGamePacketListenerImplAccessor.METHOD_SEND!!.invoke(commonGameConnection, packet)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw Error(e)
        }
    }

    /**
     * Sends one or more packets to a group of player.
     * @param players The players that are going to receive the packet(s).
     * @param packets The packet(s) that are going to be sent to the player(s).
     */
    @JvmStatic
    fun sendPacketSync(players: Collection<Player>, vararg packets: Any) {
        for (player in players) {
            sendPacketSync(player, *packets)
        }
    }

    /**
     * Sends one or more packets to a player asynchronously. Packets are thread safe.
     * @param player The player that is going to receive the packet(s).
     * @param packets The packet(s) that are going to be sent to the player.
     */
    @JvmStatic
    fun sendPacket(player: Player, vararg packets: Any): Future<*> {
        return runEAsync { sendPacketSync(player, *packets) }
    }

    /**
     * Sends one or more packets to a group of player asynchronously. Packets are thread safe.
     * @param players The players that are going to receive the packet(s).
     * @param packets The packet(s) that are going to be sent to the player(s).
     */
    @JvmStatic
    fun sendPacket(players: Collection<Player>, vararg packets: Any): Future<*> {
        return runEAsync { sendPacketSync(players, *packets) }
    }
}