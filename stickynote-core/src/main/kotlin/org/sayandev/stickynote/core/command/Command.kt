package org.sayandev.stickynote.core.command

import net.kyori.adventure.text.Component
import org.incendo.cloud.CommandManager
import org.incendo.cloud.component.CommandComponent
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.description.Description
import org.incendo.cloud.kotlin.MutableCommandBuilder
import org.incendo.cloud.kotlin.extension.buildAndRegister
import org.incendo.cloud.kotlin.extension.commandBuilder
import org.incendo.cloud.parser.standard.StringParser
import org.incendo.cloud.setting.ManagerSetting
import org.incendo.cloud.suggestion.Suggestion
import org.sayandev.stickynote.core.command.interfaces.CommandExtension
import org.sayandev.stickynote.core.command.interfaces.SenderExtension
import java.util.concurrent.CompletableFuture

abstract class Command<S: SenderExtension<*, *>>(
    val rootId: String,
    val manager: CommandManager<S>,
    val name: String,
    vararg val aliases: String,
) : CommandExtension {

    open fun rootBuilder(builder: MutableCommandBuilder<S>) { }
    open fun rootHandler(context: CommandContext<S>) { }

    private var errorPrefix = Component.empty().asComponent()

    val command: MutableCommandBuilder<S>

    fun rawCommandBuilder() = manager.commandBuilder(name, Description.empty(), aliases.toList().toTypedArray()) { }

    init {
        manager.createHelpHandler()
        manager.settings().set(ManagerSetting.OVERRIDE_EXISTING_COMMANDS, true)

        command = manager.buildAndRegister(name, Description.empty(), aliases.toList().toTypedArray()) {
            permission("$rootId.commands.${name}")
            handler { context ->
                rootHandler(context)
            }
            rootBuilder(this)
        }
    }

    override fun errorPrefix(): Component {
        return errorPrefix
    }

    override fun errorPrefix(prefix: Component) {
        errorPrefix = prefix
    }

    fun MutableCommandBuilder<*>.literalWithPermission(literal: String) {
        literal(literal)
        val partedPermission = this.build().components()
        partedPermission.removeAt(0)
        permission("${rootId}.commands.${partedPermission.map { it.name() }.distinct().joinToString(".")}")
    }
}

internal fun CommandComponent.Builder<SenderExtension<*, *>, String>.createStringSuggestion(suggestions: Collection<String>) {
    this.suggestionProvider { _, _ ->
        CompletableFuture.completedFuture(suggestions.map { Suggestion.suggestion(it) })
    }
}

fun MutableCommandBuilder<SenderExtension<*, *>>.required(name: String, suggestions: Collection<String>): MutableCommandBuilder<SenderExtension<*, *>> {
    return required(name, StringParser.stringParser()) {
        createStringSuggestion(suggestions)
    }
}

fun MutableCommandBuilder<SenderExtension<*, *>>.optional(name: String, suggestions: Collection<String>): MutableCommandBuilder<SenderExtension<*, *>> {
    return optional(name, StringParser.stringParser()) {
        createStringSuggestion(suggestions)
    }
}