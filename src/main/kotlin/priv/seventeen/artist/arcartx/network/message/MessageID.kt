/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.network.message

/** 消息类型枚举，Client 为客户端→服务端，Server 为服务端→客户端（ID 偏移 +1000） */
interface MessageID {

    enum class Client(override val id: Int) : MessageID {
        CONNECTION(0),
        KEY_GROUP_PRESS(1),
        SIMPLE_KEY_PRESS(2),
        PAPI(3),
        ENTITY_JOIN(4),
        GET_SLOT(5),
        SCREEN_DO(6),
        CUSTOM(7),
        CLICK_SLOT(8),
        INITIALIZE(9),
        SIZE_DATA(11),
        BONE_HIT(12),
        KEY_PRESS(13),
        DONE(14),
        BLOCK_MODEL(15),
        MOUSE_CLICK(16),
        UI_DATA(19),
        MOVE_BREAK(20),
        CONTAINER_CLICK(21), // 对原版容器发起槽位点击(触发 InventoryClickEvent)
    }


    enum class Server(id : Int) : MessageID {
        CONNECTION(0),
        RESOURCE_RELOAD(1),
        EXECUTE_ARIA(2),
        SETTING(3),
        BASE64_IMAGE(4),
        CUSTOM(5),
        ENTITY_GLOW(6),
        ITEM_COOLDOWN(7),
        PAPI(8),
        ENTITY_SOUND(9),
        LOCATION_SOUND(10),
        SCREEN_COMMAND(11),
        SLOT_ITEM_STACK(12),
        SHADER(13),
        SKYBOX(14),
        WORLD_CHANGE(15),
        ENTITY_INTERACTION(16),
        PLAYER_JUMP(17),
        STATE_CHANGE(18),
        CONTROLLER(19),
        SCENE_CAMERA(20),
        CLIENT_TITLE(21),
        CAMERA_SET(22),
        CAMERA_PRESET(23),
        CAMERA_LOCK_MODE(24),
        CAMERA_THIRD_PERSON(25),
        CAMERA_ELEMENT(26),
        ENTITY_MODEL(30),
        ENTITY_SIZE(31),
        ENTITY_ANIMATION(32),
        ENTITY_ANIMATION_DEFAULT_STATE(33),
        ENTITY_HIDE_BONE(34),
        ENTITY_HIDE_NAME(35),
        SCREEN(36),
        MODEL_EFFECT(37),
        WAYPOINT_CREATE(39),
        WAYPOINT_DELETE(40),
        DAMAGE_DISPLAY(41),
        ADYESHACH_JOIN(42),
        BLOCK_ANIMATION(43),
        HAMMER_CRACK(44),
        EXTRA_MODEL(45),
        CARD_MESSAGE(46),
        BLOCK_MODEL(47),
        SLOT_DEBUG(48),
        REMOVE_VARIABLE(49),
        WORLD_TEXTURE(51),
        WORLD_TEXTURE_REMOVE(52),
        OPEN_EDITOR(53),
        FILE_LIST(54),
        HIDE_HIT_BOX(55),
        NAMED_MODEL_EFFECT(56),
        REMOVE_NAMED_EFFECT(57),
        CAMERA_SHAKE(58),
        SET_USE(59),
        PLAYER_LOOK(60),
        CONTROLLER_DATA(61),
        BEDROCK_PARTICLES(62),
        FIRST_PERSON_ANIMATION(63),
        FLYING_STATE(64), // 广播玩家创造/旁观飞行态(vanilla 不同步其他玩家的 abilities.flying，客户端远端拿不到)
        COSTUME(65),
        PLAYER_VARIANT(66),
        ANIMATION_PACK(67),
        CHAT_CARD_CONFIG(68), // 单张聊天卡片模板注册/重载/注销
        ;

        override val id: Int = id
            get() = field + 1000



    }


    val id: Int

    companion object {
        /** 启动自检：校验同一枚举内 id 无重复，撞号则 fail-fast，防止新增封包静默串包 */
        fun checkDuplicates() {
            verifyUnique("Client", Client.values())
            verifyUnique("Server", Server.values())
        }

        private fun verifyUnique(group: String, values: Array<out MessageID>) {
            val seen = HashMap<Int, String>()
            for (v in values) {
                val name = (v as Enum<*>).name
                val prev = seen.put(v.id, name)
                require(prev == null) { "MessageID.$group 存在重复 id=${v.id}（$prev 与 $name），请检查编号" }
            }
        }
    }
}
