package cn.zbx1425.minopp;

import cn.zbx1425.minopp.block.BlockEntityMinoTable;
import cn.zbx1425.minopp.game.ActionReport;
import cn.zbx1425.minopp.game.CardGame;
import cn.zbx1425.minopp.game.CardPlayer;
import cn.zbx1425.minopp.item.ItemHandCards;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

public class MinoCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("minopp")
                .then(Commands.literal("shout")
                        .executes(context -> {
                            boolean success = Mino.onServerChatMessage("mino", context.getSource().getPlayerOrException());
                            if (!success) throw new SimpleCommandExceptionType(Component.translatable("game.minopp.play.no_game")).create();
                            return 1;
                        }))
                .then(Commands.literal("give_test_card").requires((commandSourceStack) -> commandSourceStack.hasPermission(2))
                        .executes(context -> {
                            ItemStack stack = new ItemStack(Mino.ITEM_HAND_CARDS.get());
                            UUID uuid = UUID.randomUUID();
                            stack.set(Mino.DATA_COMPONENT_TYPE_CARD_GAME_BINDING.get(),
                                    new ItemHandCards.CardGameBindingComponent(uuid, Optional.empty()));
                            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Test Card " + uuid.toString().substring(0, 8)));
                            context.getSource().getPlayerOrException().getInventory().add(stack);
                            return 1;
                        }))
                .then(Commands.literal("force_discard").requires((commandSourceStack) -> commandSourceStack.hasPermission(2))
                        .executes(context -> {
                            withPlayerAndGame(context, (game, player) -> {
                                ActionReport report = ActionReport.builder(game, player);
                                if (player.hand.isEmpty()) return;
                                game.doDiscardCard(player, player.hand.getFirst(), report);
                            });
                            return 1;
                        }))
                .then(Commands.literal("force_draw").requires((commandSourceStack) -> commandSourceStack.hasPermission(2))
                        .then(Commands.argument("draw_count", IntegerArgumentType.integer(1))
                            .executes(context -> {
                                withPlayerAndGame(context, (game, player) -> {
                                    ActionReport report = ActionReport.builder(game, player);
                                    game.doDrawCard(player, IntegerArgumentType.getInteger(context, "draw_count"), report);
                                });
                                return 1;
                            }))
                        .executes(context -> {
                            withPlayerAndGame(context, (game, player) -> {
                                ActionReport report = ActionReport.builder(game, player);
                                game.doDrawCard(player, 1, report);
                            });
                            return 1;
                        }))
        );
    }

    private static final SimpleCommandExceptionType ERROR_NO_GAME = new SimpleCommandExceptionType(Component.translatable("game.minopp.play.no_game"));


    private static void withPlayerAndGame(CommandContext<CommandSourceStack> context, BiConsumer<CardGame, CardPlayer> action) throws CommandSyntaxException {
        BlockPos gamePos = ItemHandCards.getHandCardGamePos(context.getSource().getPlayerOrException());
        if (gamePos == null) throw ERROR_NO_GAME.create();
        if (context.getSource().getLevel().getBlockEntity(gamePos) instanceof BlockEntityMinoTable tableEntity) {
            if (tableEntity.game == null) throw ERROR_NO_GAME.create();
            CardPlayer cardPlayer = tableEntity.game.deAmputate(ItemHandCards.getCardPlayer(context.getSource().getPlayerOrException()));
            if (cardPlayer == null) throw ERROR_NO_GAME.create();
            action.accept(tableEntity.game, cardPlayer);
            tableEntity.sync();
        } else {
            throw ERROR_NO_GAME.create();
        }
    }
}