package org.sayandev.stickynote.bukkit.nms.npc

import net.kyori.adventure.platform.bukkit.MinecraftComponentSerializer
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.sayandev.stickynote.bukkit.extension.toLocation
import org.sayandev.stickynote.bukkit.nms.NMSUtils
import org.sayandev.stickynote.bukkit.nms.NMSUtils.sendPacket
import org.sayandev.stickynote.bukkit.nms.PacketUtils
import org.sayandev.stickynote.bukkit.nms.Viewable
import org.sayandev.stickynote.bukkit.nms.enum.EntityPose
import org.sayandev.stickynote.bukkit.nms.enum.EquipmentSlot
import org.sayandev.stickynote.bukkit.nms.enum.EntityAnimation
import org.sayandev.stickynote.bukkit.plugin
import org.sayandev.stickynote.bukkit.utils.ServerVersion
import org.sayandev.stickynote.core.math.Vector3
import org.sayandev.stickynote.nms.accessors.EntityAccessor
import org.sayandev.stickynote.nms.accessors.EntityDataSerializerAccessor
import org.sayandev.stickynote.nms.accessors.SynchedEntityDataAccessor
import org.sayandev.stickynote.nms.accessors.Vec3Accessor
import java.util.*

abstract class NPC: Viewable() {

    protected val equipments: EnumMap<EquipmentSlot, Any> = EnumMap<EquipmentSlot, Any>(EquipmentSlot::class.java)
    protected val poses: MutableSet<EntityPose> = HashSet()

    private var customName: Component = Component.empty()

    lateinit var position: Vector3; private set

    lateinit var entity: Any; private set
    var entityId: Int = -1; private set

    var discarded: Boolean = false; private set

    init {
        /*TODO Pathetic
           if (!patheticMapperInitialized) {
            patheticMapperInitialized = true
            try {
                PatheticMapper.initialize(Ruom.getPlugin())
            } catch (ignore: IllegalStateException) {
            }
        }*/

        for (equipmentSlot in EquipmentSlot.entries) {
            equipments[equipmentSlot] = NMSUtils.getNmsEmptyItemStack()
        }
    }

    protected fun initialize(entity: Any) {
        this.entity = entity
        this.entityId = EntityAccessor.METHOD_GET_ID!!.invoke(entity) as Int
        if (ServerVersion.supports(16)) {
            val vec3: Any = EntityAccessor.FIELD_POSITION!!.get(entity)
            this.position = Vector3.at(
                Vec3Accessor.METHOD_X!!.invoke(vec3) as Double,
                Vec3Accessor.METHOD_Y!!.invoke(vec3) as Double,
                Vec3Accessor.METHOD_Z!!.invoke(vec3) as Double
            )
        } else {
            this.position = Vector3.at(
                EntityAccessor.FIELD_LOC_X!!.get(entity) as Double,
                EntityAccessor.FIELD_LOC_Y!!.get(entity) as Double,
                EntityAccessor.FIELD_LOC_Z!!.get(entity) as Double
            )
        }
    }

    /**
     * Looks with the given yaw and pitch
     * @param yaw The yaw to look
     * @param pitch The pitch to look
     */
    fun look(yaw: Float, pitch: Float) {
        EntityAccessor.METHOD_SET_ROT!!.invoke(entity, yaw, pitch)
        getViewers().sendPacket(
            PacketUtils.getEntityRotPacket(entityId, yaw, pitch),
            PacketUtils.getHeadRotatePacket(entity, yaw)
        )
    }

    /**
     * Looks at the given position
     * @param position The position to look at
     */
    fun lookAt(position: Vector3) {
        val targetLocation: Location = position.toLocation(null)
        val dirLocation: Location = getPosition().toLocation(null)
        dirLocation.setDirection(targetLocation.subtract(dirLocation).toVector())
        look(dirLocation.yaw, dirLocation.pitch)
    }

    /**
     * Moves the NPC by the given vector
     * @param vector The vector to move. Length of the vector shouldn't be greater than 8
     * @apiNote The length of the vector shouldn't be greater than 8
     */
    fun move(vector: Vector3) {
        setPosition(getPosition().add(vector))
        getViewers().sendPacket(PacketUtils.getEntityPosPacket(entityId, vector.x, vector.y, vector.z))
    }

    /**
     * Moves the NPC by the given vector in the given ticks
     * @param vector The vector to move
     * @param inTicks The amount of ticks to move
     * @param callback The callback to run after the movement is done
     * @apiNote The method fails without errors if the length of a calculated vector (vector / inTicks) is greater than 8
     */
    fun move(vector: Vector3, inTicks: Int, callback: () -> Unit) {
        object : BukkitRunnable() {
            var i: Int = 0
            override fun run() {
                if (i >= inTicks) {
                    callback()
                    cancel()
                    return
                }
                move(Vector3.at(
                    vector.x / inTicks,
                    vector.y / inTicks,
                    vector.z / inTicks
                ))
                i++
            }
        }.runTaskTimerAsynchronously(plugin, 0, 1)
    }

    /*TODO Pathetic
       fun moveTo(destination: Location, tickPerBlock: Int, callback: ()) {
        val pathFinder: Pathfinder = PatheticMapper.newPathfinder(
            PathingRuleSet.createAsyncRuleSet()
                .withAllowingDiagonal(true)
                .withAllowingFailFast(true)
                .withAllowingFallback(false)
                .withLoadingChunks(true)
                .withAsync(true)
        )
        pathFinder.findPath(
            BukkitMapper.toPathPosition(Vector3UtilsBukkit.toLocation(destination.world, getPosition())),
            BukkitMapper.toPathPosition(destination),
            WalkablePathfinderStrategy()
        ).thenAccept { result ->
            if (!result.successful()) {
                callback.accept(false)
            } else {
                val vectors: MutableList<Vector3> = ArrayList<Vector3>()
                val looks: MutableList<Float> = ArrayList()
                var previousVector: Vector3? = getPosition()
                for (position in result.getPath().getPositions()) {
                    val vector: Vector3 = Vector3.at(position.getX(), position.getY(), position.getZ())
                    val travelVector: Vector3 = Vector3Utils.getTravelDistance(previousVector, vector)
                    previousVector = vector
                    for (i in 0 until tickPerBlock) {
                        vectors.add(
                            Vector3.at(
                                travelVector.getX() / tickPerBlock,
                                travelVector.getY() / tickPerBlock,
                                travelVector.getZ() / tickPerBlock
                            )
                        )
                        looks.add(Vector(0, 0, 0).angle(Vector(0, 0, 0)))
                    }
                }
                object : BukkitRunnable() {
                    var index: Int = 0
                    override fun run() {
                        if (index >= vectors.size) {
                            cancel()
                            callback.accept(true)
                            return@thenAccept
                        }
                        moveAndLook(vectors[index], looks[index], 0f)
                        Bukkit.broadcastMessage("yaw: " + looks[index])
                        index++
                    }
                }.runTaskTimerAsynchronously(Ruom.getPlugin(), 0, 1)
            }
        }
    }*/

    /**
     * Moves the NPC by the given vector and looks with the given yaw and pitch
     * @param vector The vector to move. Length of the vector shouldn't be greater than 8
     * @param yaw The yaw to look
     * @param pitch The pitch to look
     * @apiNote The length of the vector shouldn't be greater than 8
     */
    fun moveAndLook(vector: Vector3, yaw: Float, pitch: Float) {
        setPosition(getPosition().add(vector))
        getViewers().sendPacket(
            PacketUtils.getEntityPosRotPacket(entityId, vector.x, vector.y, vector.z, yaw, pitch, true),
            PacketUtils.getHeadRotatePacket(entity, yaw)
        )
    }

    /**
     * Teleports the NPC to its current internal position
     */
    fun teleport() {
        getViewers().sendPacket(PacketUtils.getTeleportEntityPacket(entity))
    }

    /**
     * Teleports the NPC to the given location
     * @param location The location to teleport
     */
    fun teleport(location: Vector3) {
        setPosition(location)
        getViewers().sendPacket(PacketUtils.getTeleportEntityPacket(entity))
    }

    /**
     * Teleports the NPC to the given location with the given yaw and pitch
     * @param location The location to teleport
     * @param yaw The yaw to look
     * @param pitch The pitch to look
     */
    fun teleport(location: Vector3, yaw: Float, pitch: Float) {
        setPosition(location)
        EntityAccessor.METHOD_SET_ROT!!.invoke(entity, yaw, pitch)
        getViewers().sendPacket(PacketUtils.getTeleportEntityPacket(entity))
    }

    /**
     * Animates the NPC with the given animation
     * @param animation The animation to animate
     */
    fun animate(animation: EntityAnimation) {
        getViewers().sendPacket(PacketUtils.getAnimatePacket(entity, animation.action))
    }

    /**
     * Plays a collect item animation
     * @param collectedEntityId The entity id of the collected item
     * @param collectorEntityId The entity id of the collector
     * @param amount The amount of items collected
     */
    protected fun collect(collectedEntityId: Int, collectorEntityId: Int, amount: Int) {
        getViewers().sendPacket(PacketUtils.getCollectItemPacket(collectedEntityId, collectorEntityId, amount))
    }

    /**
     * Sets the velocity of the NPC
     * @param vector3 The velocity to set
     */
    fun setVelocity(vector3: Vector3) {
        getViewers().sendPacket(PacketUtils.getEntityVelocityPacket(entityId, vector3.x, vector3.y, vector3.z))
    }

    /**
     * Sets the equipment of the NPC
     * @param slot The slot to set the equipment
     * @param item The item to set
     */
    fun setEquipment(slot: EquipmentSlot, item: ItemStack?) {
        val nmsItem: Any = if (item == null) {
            NMSUtils.getNmsEmptyItemStack()
        } else {
            NMSUtils.getNmsItemStack(item)
        }
        equipments[slot] = nmsItem
        getViewers().sendPacket(PacketUtils.getEntityEquipmentPacket(entityId, slot, nmsItem))
    }

    /**
     * Sets the pose of the NPC
     * @param pose The pose to set
     * @param flag Whether to set or remove the pose
     */
    fun setPose(pose: EntityPose, flag: Boolean) {
        val changed: Boolean
        if (flag) {
            changed = poses.add(pose)
        } else {
            changed = poses.remove(pose)
            if (!poses.contains(EntityPose.CROUCHING) && !poses.contains(EntityPose.SWIMMING)) {
                EntityAccessor.METHOD_SET_POSE!!.invoke(entity, EntityPose.STANDING.nmsPose)
            }
        }
        if (changed) {
            if (poses.contains(EntityPose.SWIMMING)) {
                EntityAccessor.METHOD_SET_POSE!!.invoke(entity, EntityPose.SWIMMING.nmsPose)
            } else if (poses.contains(EntityPose.CROUCHING)) {
                EntityAccessor.METHOD_SET_POSE!!.invoke(entity, EntityPose.CROUCHING.nmsPose)
            }
        }
    }

    /**
     * Checks if the NPC has the given pose
     * @param pose The pose to check
     * @return Whether the NPC has the pose
     */
    fun hasPose(pose: EntityPose): Boolean {
        return poses.contains(pose)
    }

    /**
     * Sets the custom name of the NPC
     * @param component The component to set. If null, the custom name will be set to empty
     */
    fun setCustomName(component: Component?) {
        this.customName = component ?: Component.empty()
        val nmsComponent = MinecraftComponentSerializer.get().serialize(customName)
        EntityAccessor.METHOD_SET_CUSTOM_NAME!!.invoke(entity, nmsComponent)
        sendEntityData()
    }

    /**
     * Gets the custom name of the NPC
     * @return The custom name of the NPC
     */
    fun setCustomNameVisible(customNameVisible: Boolean) {
        EntityAccessor.METHOD_SET_CUSTOM_NAME_VISIBLE!!.invoke(entity, customNameVisible)
        sendEntityData()
    }

    /**
     * Checks if the custom name of the NPC is visible
     * @return Whether the custom name of the NPC is visible
     */
    fun isCustomNameVisible(): Boolean {
        return EntityAccessor.METHOD_IS_CUSTOM_NAME_VISIBLE!!.invoke(entity) as Boolean
    }

    /**
     * Sets the no gravity state of the NPC
     * @param noGravity Whether to set the no gravity state
     */
    fun setNoGravity(noGravity: Boolean) {
        EntityAccessor.METHOD_SET_NO_GRAVITY!!.invoke(entity, noGravity)
        sendEntityData()
    }

    /**
     * Checks if the NPC has no gravity state
     * @return Whether the NPC has no gravity state
     */
    fun isNoGravity(): Boolean {
        return EntityAccessor.METHOD_IS_NO_GRAVITY!!.invoke(entity) as Boolean
    }

    /**
     * Sets the frozen ticks of the NPC
     * @param ticksFrozen The ticks to set
     * @apiNote > 1.17
     */
    fun setTicksFrozen(ticksFrozen: Int) {
        if (!ServerVersion.supports(17)) return
        EntityAccessor.METHOD_SET_TICKS_FROZEN!!.invoke(entity, ticksFrozen)
        sendEntityData()
    }

    /**
     * Gets the frozen ticks of the NPC
     * @return The frozen ticks of the NPC
     * @apiNote > 1.17
     */
    fun getTicksFrozen(): Int {
        if (!ServerVersion.supports(17)) return -1
        return EntityAccessor.METHOD_GET_TICKS_FROZEN!!.invoke(entity) as Int
    }

    /**
     * Sets the metadata of the NPC
     * @param metadataId The metadata id to set
     * @param value The value to set
     */
    fun setMetadata(metadataId: Int, value: Any) {
        if (ServerVersion.supports(9)) {
            val entityDataSerializer: Any = NMSUtils.getEntityDataSerializer(value)
            SynchedEntityDataAccessor.METHOD_SET!!.invoke(
                getEntityData(),
                EntityDataSerializerAccessor.METHOD_CREATE_ACCESSOR!!.invoke(entityDataSerializer, metadataId),
                value
            )
        } else {
            SynchedEntityDataAccessor.METHOD_WATCH!!.invoke(getEntityData(), metadataId, value)
        }
        sendEntityData()
    }

    /**
     * Sets the passengers of the NPC
     * @param passengerIds The passenger ids to set
     */
    fun setPassengers(vararg passengerIds: Int) {
        if (!ServerVersion.supports(9)) return
        getViewers().sendPacket(PacketUtils.getEntityPassengersPacket(entity, *passengerIds))
    }

    /**
     * Gets the unique id (UUID) of the NPC
     * @return The unique id of the NPC
     */
    fun getUniqueId(): UUID {
        return EntityAccessor.METHOD_GET_UUID!!.invoke(entity) as UUID
    }

    /**
     * Gets the nms entity instance of the NPC
     * @return The nms entity instance of the NPC
     */
    fun getEntity(): Any {
        return entity
    }

    /**
     * Sends the entity data to the given viewer
     * @param viewer The viewer to send the entity data
     */
    fun sendEntityData(viewer: Player) {
        viewer.sendPacket(PacketUtils.getEntityDataPacket(entity))
    }

    /**
     * Sends the entity data to all viewers
     */
    fun sendEntityData() {
        getViewers().forEach(this::sendEntityData)
    }

    /**
     * Gets the entity data of the NPC
     * @return The entity data of the NPC
     */
    protected fun getEntityData(): Any {
        return EntityAccessor.METHOD_GET_ENTITY_DATA!!.invoke(entity)
    }

    /**
     * Sets the position of the NPC
     * @param position The position to set
     * @apiNote This method doesn't actually update the NPC's location. You should use teleport method to update the location
     * @see teleport
     */
    protected fun setPosition(position: Vector3) {
        this.position = position
        EntityAccessor.METHOD_SET_POSE!!.invoke(entity, position.x, position.y, position.z)
    }

    /**
     * Gets the position of the NPC
     * @return The position of the NPC
     */
    fun getPosition(): Vector3 {
        return position.copy()
    }

    /**
     * Discards the NPC and removes all viewers. New viewers cannot be added after this method is called
     */
    fun discard() {
        discarded = true
        removeViewers(getViewers())
        equipments.clear()
        poses.clear()
    }

    override fun onPreAddViewers(vararg viewers: Player) {
        check(!discarded) { "Cannot add viewers to a discarded npc." }
    }

    override fun onPostAddViewers(vararg viewers: Player) {
        for ((key, value) in equipments) {
            if (value != NMSUtils.getNmsEmptyItemStack()) {
                getViewers().sendPacket(PacketUtils.getEntityEquipmentPacket(entityId, key, value))
            }
        }
        for (viewer in viewers) {
            sendEntityData(viewer)
        }
    }

}