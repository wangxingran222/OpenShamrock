package moe.fuqiuluo.qqinterface.servlet.msg

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.tencent.mobileqq.app.QQAppInterface
import com.tencent.mobileqq.emoticon.QQSysFaceUtil
import com.tencent.mobileqq.pb.ByteStringMicro
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.qphone.base.remote.ToServiceMsg
import com.tencent.qqnt.aio.adapter.api.IAIOPttApi
import com.tencent.qqnt.kernel.nativeinterface.ArkElement
import com.tencent.qqnt.kernel.nativeinterface.FaceBubbleElement
import com.tencent.qqnt.kernel.nativeinterface.FaceElement
import com.tencent.qqnt.kernel.nativeinterface.MarkdownElement
import com.tencent.qqnt.kernel.nativeinterface.MarketFaceElement
import com.tencent.qqnt.kernel.nativeinterface.MarketFaceSupportSize
import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.PicElement
import com.tencent.qqnt.kernel.nativeinterface.PttElement
import com.tencent.qqnt.kernel.nativeinterface.QQNTWrapperUtil
import com.tencent.qqnt.kernel.nativeinterface.ReplyElement
import com.tencent.qqnt.kernel.nativeinterface.RichMediaFilePathInfo
import com.tencent.qqnt.kernel.nativeinterface.SmallYellowFaceInfo
import com.tencent.qqnt.kernel.nativeinterface.TextElement
import com.tencent.qqnt.kernel.nativeinterface.VideoElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import moe.fuqiuluo.qqinterface.servlet.CardSvc
import moe.fuqiuluo.qqinterface.servlet.GroupSvc
import moe.fuqiuluo.qqinterface.servlet.LbsSvc
import moe.fuqiuluo.qqinterface.servlet.ark.ArkAppInfo
import moe.fuqiuluo.qqinterface.servlet.ark.ArkMsgSvc
import moe.fuqiuluo.qqinterface.servlet.ark.WeatherSvc
import moe.fuqiuluo.qqinterface.servlet.transfile.FileTransfer
import moe.fuqiuluo.qqinterface.servlet.transfile.PictureResource
import moe.fuqiuluo.qqinterface.servlet.transfile.Private
import moe.fuqiuluo.qqinterface.servlet.transfile.Transfer
import moe.fuqiuluo.qqinterface.servlet.transfile.Troop
import moe.fuqiuluo.qqinterface.servlet.transfile.VideoResource
import moe.fuqiuluo.qqinterface.servlet.transfile.VoiceResource
import moe.fuqiuluo.qqinterface.servlet.transfile.trans
import moe.fuqiuluo.qqinterface.servlet.transfile.with
import moe.fuqiuluo.shamrock.helper.ActionMsgException
import moe.fuqiuluo.shamrock.helper.ContactHelper
import moe.fuqiuluo.shamrock.helper.IllegalParamsException
import moe.fuqiuluo.shamrock.helper.Level
import moe.fuqiuluo.shamrock.helper.LocalCacheHelper
import moe.fuqiuluo.shamrock.helper.LogCenter
import moe.fuqiuluo.shamrock.helper.LogicException
import moe.fuqiuluo.shamrock.helper.MessageHelper
import moe.fuqiuluo.shamrock.helper.MusicHelper
import moe.fuqiuluo.shamrock.helper.ParamsException
import moe.fuqiuluo.shamrock.tools.asBooleanOrNull
import moe.fuqiuluo.shamrock.tools.asInt
import moe.fuqiuluo.shamrock.tools.asIntOrNull
import moe.fuqiuluo.shamrock.tools.asJsonObject
import moe.fuqiuluo.shamrock.tools.asLong
import moe.fuqiuluo.shamrock.tools.asString
import moe.fuqiuluo.shamrock.tools.asStringOrNull
import moe.fuqiuluo.shamrock.tools.ifNullOrEmpty
import moe.fuqiuluo.shamrock.utils.AudioUtils
import moe.fuqiuluo.shamrock.utils.FileUtils
import moe.fuqiuluo.shamrock.utils.MediaType
import moe.fuqiuluo.shamrock.utils.PlatformUtils
import moe.fuqiuluo.shamrock.xposed.helper.AppRuntimeFetcher
import moe.fuqiuluo.shamrock.xposed.helper.NTServiceFetcher
import moe.fuqiuluo.shamrock.xposed.helper.msgService
import mqq.app.MobileQQ
import tencent.im.oidb.cmd0xb77.oidb_cmd0xb77
import tencent.im.oidb.cmd0xdc2.oidb_cmd0xdc2
import tencent.im.oidb.oidb_sso
import java.io.File
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.random.nextInt

internal typealias IMaker = suspend (Int, Long, String, JsonObject) -> Result<MsgElement>

internal object MessageMaker {
    private val makerArray = mutableMapOf(
        "text" to MessageMaker::createTextElem,
        "face" to MessageMaker::createFaceElem,
        "pic" to MessageMaker::createImageElem,
        "image" to MessageMaker::createImageElem,
        "voice" to MessageMaker::createRecordElem,
        "record" to MessageMaker::createRecordElem,
        "at" to MessageMaker::createAtElem,
        "video" to MessageMaker::createVideoElem,
        "markdown" to MessageMaker::createMarkdownElem,
        "dice" to MessageMaker::createDiceElem,
        "rps" to MessageMaker::createRpsElem,
        "poke" to MessageMaker::createPokeElem,
        "anonymous" to MessageMaker::createAnonymousElem,
        "share" to MessageMaker::createShareElem,
        "contact" to MessageMaker::createContactElem,
        "location" to MessageMaker::createLocationElem,
        "music" to MessageMaker::createMusicElem,
        "reply" to MessageMaker::createReplyElem,
        "touch" to MessageMaker::createTouchElem,
        "weather" to MessageMaker::createWeatherElem,
        "json" to MessageMaker::createJsonElem,
        "new_dice" to MessageMaker::createNewDiceElem,
        "new_rps" to MessageMaker::createNewRpsElem,
        "basketball" to MessageMaker::createBasketballElem,
        //"node" to MessageMaker::createNodeElem,
        //"multi_msg" to MessageMaker::createLongMsgStruct,
        "bubble_face" to MessageMaker::createBubbleFaceElem,
    )

    private suspend fun createBubbleFaceElem(chatType: Int, msgId: Long, peerId: String, data: JsonObject): Result<MsgElement> {
        data.checkAndThrow("id", "count")
        val faceId = data["id"].asInt
        val local = QQSysFaceUtil.convertToLocal(faceId)
        val name = QQSysFaceUtil.getFaceDescription(local)
        val count = data["count"].asInt
        val elem = MsgElement()
        elem.elementType = MsgConstant.KELEMTYPEFACEBUBBLE
        val face = FaceBubbleElement()
        face.faceType = 13
        face.faceCount = count
        face.faceSummary = QQSysFaceUtil.getPrueFaceDescription(name)
        val smallYellowFaceInfo = SmallYellowFaceInfo()
        smallYellowFaceInfo.index = faceId
        smallYellowFaceInfo.compatibleText = face.faceSummary
        smallYellowFaceInfo.text = face.faceSummary
        face.yellowFaceInfo = smallYellowFaceInfo
        face.faceFlag = 0
        face.content = data["text"].asStringOrNull ?: "[${face.faceSummary}]x$count"
        elem.faceBubbleElement = face
        return Result.success(elem)
    }

//    private suspend fun createNodeElem(
//        chatType: Int,
//        msgId: Long,
//        peerId: String,
//        data: JsonObject
//    ): Result<MsgElement> {
//        data.checkAndThrow("data")
//        SendForwardMessage(MsgConstant.KCHATTYPEC2C, TicketSvc.getUin(), data["content"].asJsonArray)
//
//    }
    /**\
     *         msgElement.setFaceElement(new FaceElement());
     *         msgElement.getFaceElement().setFaceIndex(114);
     *         msgElement.getFaceElement().setFaceText("/篮球");
     *         msgElement.getFaceElement().setFaceType(3);
     *         msgElement.getFaceElement().setPackId("1");
     *         msgElement.getFaceElement().setStickerId("13");
     *         msgElement.getFaceElement().setRandomType(1);
     *         msgElement.getFaceElement().setImageType(1);
     *         msgElement.getFaceElement().setStickerType(2);
     *         msgElement.getFaceElement().setSourceType(1);
     *         msgElement.getFaceElement().setSurpriseId("");
     *         msgElement.getFaceElement().setResultId(String.valueOf(new Random().nextInt(5) + 1));
     */
    private suspend fun createBasketballElem(chatType: Int, msgId: Long, peerId: String, data: JsonObject): Result<MsgElement> {
        val elem = MsgElement()
        elem.elementType = MsgConstant.KELEMTYPEFACE
        val face = FaceElement()
        face.faceIndex = 114
        face.faceText = "/篮球"
        face.faceType = 3
        face.packId = "1"
        face.stickerId = "13"
        face.sourceType = 1
        face.stickerType = 2
        face.resultId = Random.nextInt(1 .. 5).toString()
        face.surpriseId = ""
        face.randomType = 1
        elem.faceElement = face
        return Result.success(elem)
    }

    private suspend fun createNewRpsElem(chatType: Int, msgId: Long, peerId: String, data: JsonObject): Result<MsgElement> {
        val elem = MsgElement()
        elem.elementType = MsgConstant.KELEMTYPEFACE
        val face = FaceElement()
        face.faceIndex = 359
        face.faceText = "/包剪锤"
        face.faceType = 3
        face.packId = "1"
        face.stickerId = "34"
        face.sourceType = 1
        face.stickerType = 2
        face.resultId = ""
        face.surpriseId = ""
        face.randomType = 1
        elem.faceElement = face
        return Result.success(elem)
    }

    private suspend fun createNewDiceElem(chatType: Int, msgId: Long, peerId: String, data: JsonObject): Result<MsgElement> {
        val elem = MsgElement()
        elem.elementType = MsgConstant.KELEMTYPEFACE
        val face = FaceElement()
        face.faceIndex = 358
        face.faceText = "/骰子"
        face.faceType = 3
        face.packId = "1"
        face.stickerId = "33"
        face.sourceType = 1
        face.stickerType = 2
        face.resultId = ""
        face.surpriseId = ""
        face.randomType = 1
        elem.faceElement = face
        return Result.success(elem)
    }

    private suspend fun createJsonElem(
        chatType: Int,
        msgId: Long,
        peerId: String,
        data: JsonObject
    ): Result<MsgElement> {
        data.checkAndThrow("data")
        val jsonStr = data["data"].let {
            if (it is JsonObject) it.asJsonObject.toString() else {
                val str = it.asStringOrNull ?: ""
                // 检查字符串是否是合法json，不然qq会闪退
                try {
                    val element = Json.decodeFromString<JsonElement>(str)
                    if (element !is JsonObject) {
                        return Result.failure(Exception("不合法的JSON字符串"))
                    }
                } catch (err: Throwable) {
                    LogCenter.log(err.stackTraceToString(), Level.ERROR)
                    return Result.failure(Exception("不合法的JSON字符串"))
                }
                str
            }
        }
        val element = MsgElement()
        element.elementType = MsgConstant.KELEMTYPEARKSTRUCT
        val ark = ArkElement(jsonStr, null, null)
        element.arkElement = ark
        return Result.success(element)
    }

    private suspend fun createTouchElem(
        chatType: Int,
        msgId: Long,
        peerId: String,
        data: JsonObject
    ): Result<MsgElement> {
        data.checkAndThrow("id")
        GroupSvc.poke(peerId, data["id"].asString)
        return Result.failure(ActionMsgException)
    }

    private suspend fun createWeatherElem(
        chatType: Int,
        msgId: Long,
        peerId: String,
        data: JsonObject
    ): Result<MsgElement> {
        var code = data["code"].asIntOrNull

        if (code == null) {
            data.checkAndThrow("city")
            val city = data["city"].asString
            code = WeatherSvc.searchCity(city).onFailure {
                LogCenter.log("无法获取城市天气: $city", Level.ERROR)
            }.getOrNull()?.firstOrNull()?.adcode
        }

        if (code != null) {
            WeatherSvc.fetchWeatherCard(code).onSuccess {
                val element = MsgElement()
                element.elementType = MsgConstant.KELEMTYPEARKSTRUCT
                val share = it["weekStore"]
                    .asJsonObject["share"]
                    .asJsonObject["data"].toString()

                element.arkElement =
                    ArkElement(share, null, MsgConstant.ARKSTRUCTELEMENTSUBTYPEUNKNOWN)

                return Result.success(element)
            }.onFailure {
                LogCenter.log("无法发送天气分享", Level.ERROR)
            }
        }

        return Result.failure(ActionMsgException)
    }

    private suspend fun createReplyElem(
        chatType: Int,
        msgId: Long,
        peerId: String,
        data: JsonObject
    ): Result<MsgElement> {
        data.checkAndThrow("id")
        val element = MsgElement()
        element.elementType = MsgConstant.KELEMTYPEREPLY
        val reply = ReplyElement()

        val msgHash = data["id"].asInt
        val mapping = MessageHelper.getMsgMappingByHash(msgHash)
            ?: return Result.failure(Exception("不存在该消息映射，无法回复消息"))

        reply.replayMsgId = mapping.qqMsgId
        reply.sourceMsgIdInRecords = mapping.qqMsgId

        if (reply.replayMsgId == 0L) {
            // 貌似获取失败了，555
            LogCenter.log("无法获取被回复消息", Level.ERROR)
        }

        if(data.containsKey("text")) {
            data.checkAndThrow("qq", "time", "seq")
            reply.replayMsgSeq = data["seq"].asLong
            reply.sourceMsgText = data["text"].asString
            reply.replyMsgTime = data["time"].asLong
            reply.senderUid = data["qq"].asString.toLong()
        }
        element.replyElement = reply
        return Result.success(element)
    }

    private suspend fun createMusicElem(
        chatType: Int,
        msgId: Long,
        peerId: String,
        data: JsonObject
    ): Result<MsgElement> {
        data.checkAndThrow("type")

        when(val type = data["type"].asString) {
            "qq" -> {
                data.checkAndThrow("id")
                val id = data["id"].asString
                if(!MusicHelper.tryShareQQMusicById(chatType, peerId.toLong(), msgId, id)) {
                    LogCenter.log("无法发送QQ音乐分享", Level.ERROR)
                }
            }
            "163" -> {
                data.checkAndThrow("id")
                val id = data["id"].asString
                if(!MusicHelper.tryShare163MusicById(chatType, peerId.toLong(), msgId, id)) {
                    LogCenter.log("无法发送网易云音乐分享", Level.ERROR)
                }
            }
            "custom" -> {
                data.checkAndThrow("url", "audio", "title")
                ArkMsgSvc.tryShareMusic(
                    chatType,
                    peerId.toLong(),
                    msgId,
                    ArkAppInfo.QQMusic,
                    data["title"].asString,
                    data["singer"].asStringOrNull ?: "",
                    data["url"].asString,
                    data["image"].asStringOrNull ?: "",
                    data["audio"].asString
                )
            }
            else -> LogCenter.log("不支持的音乐分享类型: $type", Level.ERROR)
        }

        return Result.failure(ActionMsgException)
    }

    private suspend fun createLocationElem(chatType: Int, msgId: Long, peerId: String, data: JsonObject): Result<MsgElement> {
        data.checkAndThrow("lat", "lon")

        val lat = data["lat"].asString.toDouble()
        val lon = data["lon"].asString.toDouble()

        LbsSvc.tryShareLocation(chatType, peerId.toLong(), lat, lon).onFailure {
            LogCenter.log("无法发送位置分享", Level.ERROR)
        }

        return Result.failure(ActionMsgException)
    }

    private suspend fun createContactElem(chatType: Int, msgId: Long, peerId: String, data: JsonObject): Result<MsgElement> {
        data.checkAndThrow("id")
        val type = data["type"].asStringOrNull ?: data["kind"].asStringOrNull
        val id = data["id"].asString
        val elem = MsgElement()

        when (type) {
            "private", "qq" -> {
                val ark = ArkElement(CardSvc.getSharePrivateArkMsg(id.toLong()), null, null)
                elem.arkElement = ark
            }
            "group" -> {
                val ark = ArkElement(GroupSvc.getShareTroopArkMsg(id.toLong()), null, null)
                elem.arkElement = ark
            }
            else -> throw IllegalParamsException("type")
        }

        elem.elementType = MsgConstant.KELEMTYPEARKSTRUCT

        return Result.success(elem)
    }

    private suspend fun createShareElem(chatType: Int, msgId: Long, peerId: String, data: JsonObject): Result<MsgElement> {
        data.checkAndThrow("title", "url")

        val url = data["url"].asString
        val image = if (data.containsKey("image")) {
            data["image"].asString
        } else {
            val startWithPrefix = url.startsWith("http://") || url.startsWith("https://")
            val endWithPrefix = url.startsWith("/")
            "http://" + url.split("/")[if (startWithPrefix) 2 else 0] + if (!endWithPrefix) {
                "/favicon.ico"
            } else {
                "favicon.ico"
            }
        }
        val title = data["title"].asString
        val content = data["content"].asStringOrNull

        val reqBody = oidb_cmd0xdc2.ReqBody()
        val info = oidb_cmd0xb77.ReqBody()
        info.appid.set(100446242L)
        info.app_type.set(1)
        info.msg_style.set(0)
        info.recv_uin.set(peerId.toLong())
        val clientInfo = oidb_cmd0xb77.ClientInfo()
        clientInfo.platform.set(1)
        info.client_info.set(clientInfo)
        val richMsgBody = oidb_cmd0xb77.RichMsgBody()
        richMsgBody.using_ark.set(true)
        richMsgBody.title.set(title)
        richMsgBody.summary.set(content ?: url)
        richMsgBody.brief.set("[分享] $title")
        richMsgBody.url.set(url)
        richMsgBody.picture_url.set(image)
        info.ext_info.set(oidb_cmd0xb77.ExtInfo().also {
            it.msg_seq.set(msgId)
        })
        info.rich_msg_body.set(richMsgBody)
        reqBody.msg_body.set(info)
        val sendTo = oidb_cmd0xdc2.BatchSendReq()
        when (chatType) {
            MsgConstant.KCHATTYPEGROUP -> sendTo.send_type.set(1)
            MsgConstant.KCHATTYPEC2C -> sendTo.send_type.set(0)
            else -> return createTextElem(
                chatType = chatType,
                msgId = msgId,
                peerId = peerId,
                data = JsonObject(mapOf("text" to JsonPrimitive("[分享] $title\n地址: $url")))
            )
        }
        sendTo.recv_uin.set(peerId.toLong())
        reqBody.batch_send_req.add(sendTo)
        val app = AppRuntimeFetcher.appRuntime as QQAppInterface
        val to = ToServiceMsg("mobileqq.service", app.currentAccountUin, "OidbSvc.0xdc2_34")
        val oidb = oidb_sso.OIDBSSOPkg()
        oidb.uint32_command.set(0xdc2)
        oidb.uint32_service_type.set(34)
        oidb.bytes_bodybuffer.set(ByteStringMicro.copyFrom(reqBody.toByteArray()))
        oidb.str_client_version.set(PlatformUtils.getClientVersion(MobileQQ.getContext()))
        to.putWupBuffer(oidb.toByteArray())
        to.addAttribute("req_pb_protocol_flag", true)
        app.sendToService(to)
        return Result.failure(ActionMsgException)
    }

    private suspend fun createAnonymousElem(chatType: Int, msgId: Long, peerId: String, data: JsonObject): Result<MsgElement> {
        return Result.failure(ActionMsgException)
    }

    private suspend fun createPokeElem(chatType: Int, msgId: Long, peerId: String, data: JsonObject): Result<MsgElement> {
        data.checkAndThrow("type", "id")
        val elem = MsgElement()
        val face = FaceElement()
        face.faceIndex = 0
        face.faceText = ""
        face.faceType = 5
        face.packId = null
        face.pokeType = data["type"].asInt
        face.spokeSummary = ""
        face.doubleHit = 0
        face.vaspokeId = data["id"].asInt
        face.vaspokeName = ""
        face.vaspokeMinver = ""
        face.pokeStrength = (data["strength"].asIntOrNull ?: data["cnt"].asIntOrNull
                ?: data["count"].asIntOrNull ?: data["time"].asIntOrNull ?: 0).also {
            if(it < 0 || it > 3) throw IllegalParamsException("strength")
        }
        face.msgType = 0
        face.faceBubbleCount = 0
        face.oldVersionStr = "[截一戳]请使用最新版手机QQ体验新功能。"
        face.pokeFlag = 0
        elem.elementType = MsgConstant.KELEMTYPEFACE
        elem.faceElement = face
        return Result.success(elem)
    }

    private suspend fun createFaceElem(chatType: Int, msgId: Long, peerId: String, data: JsonObject): Result<MsgElement> {
        data.checkAndThrow("id")

        val serverId = data["id"].asInt
        val big = (data["big"].asBooleanOrNull ?: false) || serverId == 394 || serverId == 393 || serverId == 392

        val elem = MsgElement()
        elem.elementType = MsgConstant.KELEMTYPEFACE
        val face = FaceElement()

        // 1 old face
        // 2 normal face
        // 3 super face
        // 4 is market face
        // 5 is vas poke
        face.faceType = if (big) 3 else 2
        face.faceIndex = serverId
        face.faceText = QQSysFaceUtil.getFaceDescription(QQSysFaceUtil.convertToLocal(serverId))
        if (serverId == 394) {
            face.stickerId = 40.toString()
            face.packId = "1"
            face.sourceType = 1
            face.stickerType = 3
            face.randomType = 1
            face.resultId = data["result"].asStringOrNull ?: Random.nextInt(1 .. 5).toString()
        } else if (serverId == 393) {
            face.stickerId = 39.toString()
            face.packId = "1"
            face.sourceType = 1
            face.stickerType = 3
            face.randomType = 1
        } else if (serverId == 392) {
            face.stickerId = 38.toString()
            face.packId = "1"
            face.sourceType = 1
            face.stickerType = 3
            face.randomType = 1
        } else if (big) {
            face.imageType = 0
            face.stickerId = 30.toString()
            face.packId = "1"
            face.sourceType = 1
            face.stickerType = 1
            face.randomType = 1
        } else {
            face.imageType = 0
            face.packId = "0"
        }
        elem.faceElement = face

        return Result.success(elem)
    }

    private suspend fun createRpsElem(chatType: Int, msgId: Long, peerId: String, data: JsonObject): Result<MsgElement> {
        val elem = MsgElement()
        elem.elementType = MsgConstant.KELEMTYPEMARKETFACE
        val market = MarketFaceElement(
            6, 1, 11415, 3, 0, 200, 200,
            "[猜拳]", "83C8A293AE65CA140F348120A77448EE", "7de39febcf45e6db",
            null, null, 0, 0, 0, 1, 0,
            null, null, null,
            "", null, null,
            null, null, arrayListOf(MarketFaceSupportSize(200, 200)), null
        )
        elem.marketFaceElement = market
        return Result.success(elem)
    }

    private suspend fun createDiceElem(chatType: Int, msgId: Long, peerId: String, data: JsonObject): Result<MsgElement> {
        val elem = MsgElement()
        elem.elementType = MsgConstant.KELEMTYPEMARKETFACE
        val market = MarketFaceElement(
            6, 1, 11464, 3, 0, 200, 200,
            "[骰子]", "4823d3adb15df08014ce5d6796b76ee1", "409e2a69b16918f9",
            null, null, 0, 0, 0, 1, 0,
            null, null, null, // jumpurl
            "", null, null,
            null, null, arrayListOf(MarketFaceSupportSize(200, 200)), null
        )
        elem.marketFaceElement = market
        return Result.success(elem)
    }

    private suspend fun createMarkdownElem(chatType: Int, msgId: Long, peerId: String, data: JsonObject): Result<MsgElement> {
        data.checkAndThrow("text")
        val elem = MsgElement()
        elem.elementType = MsgConstant.KELEMTYPEMARKDOWN
        val markdown = MarkdownElement(data["text"].asString)
        elem.markdownElement = markdown
        return Result.success(elem)
    }

    private suspend fun createVideoElem(chatType: Int, msgId: Long, peerId: String, data: JsonObject): Result<MsgElement> {
        data.checkAndThrow("file")

        val file = data["file"].asString.let {
            val md5 = it.replace(regex = "[{}\\-]".toRegex(), replacement = "").split(".")[0].lowercase()
            var file = if (md5.length == 32) {
                FileUtils.getFile(it)
            } else {
                FileUtils.parseAndSave(it)
            }
            if (!file.exists() && data.containsKey("url")) {
                file = FileUtils.parseAndSave(data["url"].asString)
            }
            return@let file
        }
        if (!file.exists()) {
            throw LogicException("Video(${file.name}) file is not exists, please check your filename.")
        }
        val elem = MsgElement()
        val video = VideoElement()

        video.videoMd5 = QQNTWrapperUtil.CppProxy.genFileMd5Hex(file.absolutePath)

        val msgService = NTServiceFetcher.kernelService.msgService!!
        val originalPath = msgService.getRichMediaFilePathForMobileQQSend(
            RichMediaFilePathInfo(
                5, 2, video.videoMd5, file.name, 1, 0, null, "", true
            )
        )
        val thumbPath = msgService.getRichMediaFilePathForMobileQQSend(
            RichMediaFilePathInfo(
                5, 1, video.videoMd5, file.name, 2, 0, null, "", true
            )
        )
        if (!QQNTWrapperUtil.CppProxy.fileIsExist(originalPath) || QQNTWrapperUtil.CppProxy.getFileSize(
                originalPath
            ) != file.length()) {
            QQNTWrapperUtil.CppProxy.copyFile(file.absolutePath, originalPath)
            AudioUtils.obtainVideoCover(file.absolutePath, thumbPath!!)
        }

        Transfer with when (chatType) {
            MsgConstant.KCHATTYPEGROUP -> Troop(peerId)
            MsgConstant.KCHATTYPEC2C -> Private(peerId)
            else -> error("Not supported chatType($chatType) for VideoMsg")
        } trans VideoResource(file, File(thumbPath.toString()))

        video.fileTime = AudioUtils.getVideoTime(file)
        video.fileSize = file.length()
        video.fileName = file.name
        video.fileFormat = FileTransfer.VIDEO_FORMAT_MP4
        video.thumbSize = QQNTWrapperUtil.CppProxy.getFileSize(thumbPath).toInt()
        val options = BitmapFactory.Options()
        BitmapFactory.decodeFile(thumbPath, options)
        video.thumbWidth = options.outWidth
        video.thumbHeight = options.outHeight
        video.thumbMd5 = QQNTWrapperUtil.CppProxy.genFileMd5Hex(thumbPath)
        video.thumbPath = hashMapOf(0 to thumbPath)

        elem.videoElement = video
        elem.elementType = MsgConstant.KELEMTYPEVIDEO

        return Result.success(elem)
    }

    private suspend fun createAtElem(chatType: Int, msgId: Long, peerId: String, data: JsonObject): Result<MsgElement> {
        if (chatType != MsgConstant.KCHATTYPEGROUP) {
            return Result.failure(ActionMsgException)
        }
        data.checkAndThrow("qq")

        val elem = MsgElement()
        val qq = data["qq"].asString

        val at = TextElement()
        when(qq) {
            "0", "all" -> {
                at.content = "@全体成员"
                at.atType = MsgConstant.ATTYPEALL
                at.atNtUid = "0"
            }
            "online" -> {
                at.content = "@在线成员"
                at.atType = MsgConstant.ATTYPEONLINE
                at.atNtUid = "0"
            }
            "admin" -> {
                at.content = "@管理员"
                at.atRoleId = 1
                at.atType = MsgConstant.ATTYPEROLE
                at.atNtUid = "0"
            }
            else -> {
                val info = GroupSvc.getTroopMemberInfoByUin(peerId, qq, true).onFailure {
                    LogCenter.log("无法获取群成员信息: $qq", Level.ERROR)
                }.getOrNull()
                if (info != null) {
                    at.content = "@${
                        info.troopnick
                            .ifNullOrEmpty(info.friendnick)
                            .ifNullOrEmpty(qq)
                    }"
                } else {
                    at.content = "@${data["name"].asStringOrNull.ifNullOrEmpty(qq)}"
                }
                at.atType = MsgConstant.ATTYPEONE
                at.atNtUid = ContactHelper.getUidByUinAsync(qq.toLong())
            }
        }

        elem.textElement = at
        elem.elementType = MsgConstant.KELEMTYPETEXT

        return Result.success(elem)
    }

    private suspend fun createRecordElem(chatType: Int, msgId: Long, peerId: String, data: JsonObject): Result<MsgElement> {
        var file = data["file"].asStringOrNull?.let {
            val md5 = it.replace(regex = "[{}\\-]".toRegex(), replacement = "")
                .replace(" ", "")
                .split(".")[0].lowercase()
            if (md5.length == 32) {
                LocalCacheHelper.getCachePttFile(md5)
            } else {
                FileUtils.parseAndSave(it)
            }
        }
        if (file == null || (!file.exists() && data.containsKey("url"))) {
            file = FileUtils.parseAndSave(data["url"].asString)
        }
        if (!file.exists()) {
            return Result.failure(LogicException("Voice(${file.name}) file is not exists, please check your filename."))
        }
        val isMagic = data["magic"].asStringOrNull == "1"

        val ptt = PttElement()

        when (AudioUtils.getMediaType(file)) {
            MediaType.Silk -> {
                LogCenter.log({ "Silk: $file" }, Level.DEBUG)
                ptt.formatType = MsgConstant.KPTTFORMATTYPESILK
                ptt.duration = QRoute.api(IAIOPttApi::class.java)
                    .getPttFileDuration(file.absolutePath)
            }
            MediaType.Amr -> {
                LogCenter.log({ "Amr: $file" }, Level.DEBUG)
                ptt.duration = AudioUtils.getDurationSec(file)
                ptt.formatType = MsgConstant.KPTTFORMATTYPEAMR
            }
            MediaType.Pcm -> {
                LogCenter.log({ "Pcm To Silk: $file" }, Level.DEBUG)
                val result = AudioUtils.pcmToSilk(file)
                ptt.duration = (result.second * 0.001).roundToInt()
                file = result.first
                ptt.formatType = MsgConstant.KPTTFORMATTYPESILK
            }
            else -> {
                LogCenter.log({ "Audio To SILK: $file" }, Level.DEBUG)
                val result = AudioUtils.audioToSilk(file)
                ptt.duration = result.first
                file = result.second
                ptt.formatType = MsgConstant.KPTTFORMATTYPESILK
            }
        }
        //val msgService = NTServiceFetcher.kernelService.msgService!!
        //val originalPath = msgService.getRichMediaFilePathForMobileQQSend(RichMediaFilePathInfo(
        //    MsgConstant.KELEMTYPEPTT, 0, ptt.md5HexStr, file.name, 1, 0, null, "", true
        //))!!
        //if (!QQNTWrapperUtil.CppProxy.fileIsExist(originalPath) || QQNTWrapperUtil.CppProxy.getFileSize(originalPath) != file.length()) {
        //    QQNTWrapperUtil.CppProxy.copyFile(file.absolutePath, originalPath)
        //}

        if(!(Transfer with when (chatType) {
            MsgConstant.KCHATTYPEGROUP -> Troop(peerId)
            MsgConstant.KCHATTYPEC2C -> Private(peerId)
            MsgConstant.KCHATTYPETEMPC2CFROMGROUP -> Private(peerId)
            else -> error("Not supported chatType($chatType) for RecordMsg")
        } trans VoiceResource(file))) {
            return Result.failure(RuntimeException("上传语音失败: $file"))
        }

        val elem = MsgElement()
        elem.elementType = MsgConstant.KELEMTYPEPTT
        ptt.md5HexStr = QQNTWrapperUtil.CppProxy.genFileMd5Hex(file.absolutePath)

        ptt.fileName = file.name
        ptt.filePath = file.absolutePath
        ptt.fileSize = file.length()

        if (!isMagic) {
            ptt.voiceType = MsgConstant.KPTTVOICETYPESOUNDRECORD
            ptt.voiceChangeType = MsgConstant.KPTTVOICECHANGETYPENONE
        } else {
            ptt.voiceType = MsgConstant.KPTTVOICETYPEVOICECHANGE
            ptt.voiceChangeType = MsgConstant.KPTTVOICECHANGETYPEECHO
        }

        ptt.canConvert2Text = false
        ptt.fileId = 0
        ptt.fileUuid = ""
        ptt.text = ""

        elem.pttElement = ptt

        return Result.success(elem)
    }

    private suspend fun createImageElem(chatType: Int, msgId: Long, peerId: String, data: JsonObject): Result<MsgElement> {
        val isOriginal = data["original"].asBooleanOrNull ?: true
        val isFlash = data["flash"].asBooleanOrNull ?: false
        val filePath = data["file"].asStringOrNull
        val url = data["url"].asStringOrNull
        var file: File? = null
        if (filePath != null) {
            val md5 = filePath.replace(regex = "[{}\\-]".toRegex(), replacement = "").split(".")[0].lowercase()
            file = if (md5.length == 32) {
                FileUtils.getFile(md5)
            } else {
                FileUtils.parseAndSave(filePath)
            }
        }
        if ((file == null || !file.exists()) && url != null) {
            file = FileUtils.parseAndSave(url)
        }
        if (file?.exists() == false) {
            throw LogicException("Image(${file.name}) file is not exists, please check your filename.")
        }
        requireNotNull(file)

        Transfer with when (chatType) {
            MsgConstant.KCHATTYPEGROUP -> Troop(peerId)
            MsgConstant.KCHATTYPEC2C -> Private(peerId)
            else -> error("Not supported chatType($chatType) for PictureMsg")
        } trans PictureResource(file)

        val elem = MsgElement()
        elem.elementType = MsgConstant.KELEMTYPEPIC
        val pic = PicElement()
        pic.md5HexStr = QQNTWrapperUtil.CppProxy.genFileMd5Hex(file.absolutePath)

        val msgService = NTServiceFetcher.kernelService.msgService!!
        val originalPath = msgService.getRichMediaFilePathForMobileQQSend(
            RichMediaFilePathInfo(
                2, 0, pic.md5HexStr, file.name, 1, 0, null, "", true
            )
        )
        if (!QQNTWrapperUtil.CppProxy.fileIsExist(originalPath) || QQNTWrapperUtil.CppProxy.getFileSize(
                originalPath
            ) != file.length()) {
            val thumbPath = msgService.getRichMediaFilePathForMobileQQSend(
                RichMediaFilePathInfo(
                    2, 0, pic.md5HexStr, file.name, 2, 720, null, "", true
                )
            )
            QQNTWrapperUtil.CppProxy.copyFile(file.absolutePath, originalPath)
            QQNTWrapperUtil.CppProxy.copyFile(file.absolutePath, thumbPath)
        }

        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, options)
        val exifInterface = ExifInterface(file.absolutePath)
        val orientation = exifInterface.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )
        if (orientation != ExifInterface.ORIENTATION_ROTATE_90 && orientation != ExifInterface.ORIENTATION_ROTATE_270) {
            pic.picWidth = options.outWidth
            pic.picHeight = options.outHeight
        } else {
            pic.picWidth = options.outHeight
            pic.picHeight = options.outWidth
        }
        pic.sourcePath = file.absolutePath
        pic.fileSize = QQNTWrapperUtil.CppProxy.getFileSize(file.absolutePath)
        pic.original = isOriginal
        pic.picType = FileUtils.getPicType(file)
        // GO-CQHTTP扩展参数 支持
        pic.picSubType = data["subType"].asIntOrNull ?: 0
        pic.isFlashPic = isFlash

        elem.picElement = pic

        return Result.success(elem)
    }

    private suspend fun createTextElem(chatType: Int, msgId: Long, peerId: String, data: JsonObject): Result<MsgElement> {
        data.checkAndThrow("text")
        val elem = MsgElement()
        elem.elementType = MsgConstant.KELEMTYPETEXT
        val text = TextElement()
        text.content = data["text"].asString
        elem.textElement = text
        return Result.success(elem)
    }

    private fun JsonObject.checkAndThrow(vararg key: String) {
        key.forEach {
            if (!containsKey(it)) throw ParamsException(it)
        }
    }

    operator fun get(type: String): IMaker? = makerArray[type]
}
