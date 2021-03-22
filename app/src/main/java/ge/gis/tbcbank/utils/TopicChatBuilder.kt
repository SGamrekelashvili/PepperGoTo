package ge.gis.tbcbank.utils

import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.conversation.QiChatbot

import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.`object`.conversation.*
import com.aldebaran.qi.sdk.`object`.locale.Language
import com.aldebaran.qi.sdk.`object`.locale.Locale
import com.aldebaran.qi.sdk.`object`.locale.Region
import com.aldebaran.qi.sdk.builder.ChatBuilder
import com.aldebaran.qi.sdk.builder.QiChatbotBuilder
import com.aldebaran.qi.sdk.builder.TopicBuilder

object TopicChatBuilder {

    var qiContext: QiContext? = null
    var qiChatbot: QiChatbot? = null
    var chat: Chat? = null
    var topic: Topic? = null
    var chatFuture: Future<Void>? = null
    var language: Locale = Locale(Language.ENGLISH, Region.UNITED_STATES)


    fun buildChat(qiContext: QiContext, topicAssetName: String): Chat
    {

        topic = TopicBuilder.with(qiContext)
            .withAsset(topicAssetName)
            .build()

        qiChatbot = QiChatbotBuilder.with(qiContext)
            .withTopic(topic)
            .withLocale(language)
            .build()

        chat = ChatBuilder.with(qiContext)
            .withChatbot(qiChatbot)
            .withLocale(language)
            .build()


        return chat!!
    }

    fun runChat(chat: Chat) {
        if (chat == null) return
        chatFuture = chat.async().run()
    }

    fun cancelChat(chat: Chat) {
        if (chat != null) return
        chatFuture!!.requestCancellation()
    }

}