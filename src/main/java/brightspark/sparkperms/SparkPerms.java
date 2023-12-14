package brightspark.sparkperms;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import me.lucko.fabric.api.permissions.v0.OfflinePermissionCheckEvent;
import me.lucko.fabric.api.permissions.v0.PermissionCheckEvent;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.util.TriState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.*;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class SparkPerms implements ModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger(SparkPerms.class);
	private static final String PERMS_FILE_NAME = "sparkperms.txt";
	private static final String REGEX_PERMISSION = "^(?:\\w+\\.?)+$";
	private static final String COMMAND_PERM = "command.sparkperms";
	private static final DynamicCommandExceptionType EXCEPTION_INVALID_PERM =
		new DynamicCommandExceptionType(o -> Text.of("'" + o + "' is not a valid permission name"));
	private final HashSet<String> allowedPerms = new HashSet<>();
	private Path permsPath;

	@Override
	public void onInitialize() {
		permsPath = FabricLoader.getInstance().getConfigDir().resolve(PERMS_FILE_NAME);
		loadPerms();

		PermissionCheckEvent.EVENT.register((source, permission) -> checkPermission(permission));
		OfflinePermissionCheckEvent.EVENT.register((uuid, permission) ->
			CompletableFuture.completedFuture(checkPermission(permission))
		);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(createCommand())
		);
	}

	private void loadPerms() {
		LOG.atInfo().setMessage("Loading perms from {}").addArgument(permsPath).log();
		allowedPerms.clear();
		if (Files.exists(permsPath)) {
			try (BufferedReader reader = Files.newBufferedReader(permsPath)) {
				reader.lines().forEach(allowedPerms::add);
			} catch (IOException e) {
				LOG.atError().setMessage("Error loading perms from {}").setCause(e).addArgument(permsPath).log();
			}
		} else {
			try {
				Files.createFile(permsPath);
			} catch (IOException e) {
				LOG.atError().setMessage("Error creating new perms file {}").setCause(e).addArgument(permsPath).log();
			}
		}
		LOG.atInfo().setMessage("Loaded {} perms").addArgument(allowedPerms::size).log();
	}

	private void writePerms() {
		LOG.atInfo().setMessage("Writing perms to {}").addArgument(permsPath).log();
		try (BufferedWriter writer = Files.newBufferedWriter(permsPath, WRITE, CREATE, TRUNCATE_EXISTING)) {
			List<String> allowedPermsSorted = allowedPerms.stream().sorted().toList();
			for (String perm : allowedPermsSorted) {
				writer.write(perm);
				writer.newLine();
			}
		} catch (IOException e) {
			LOG.atError().setMessage("Error writing perms to {}").setCause(e).addArgument(permsPath).log();
		}
		LOG.atInfo().setMessage("Wrote {} perms").addArgument(allowedPerms::size).log();
	}

	private TriState checkPermission(String permission) {
		if (allowedPerms.stream().anyMatch(permission::startsWith)) {
			LOG.atInfo().setMessage("Allowing command with permission {}").addArgument(permission).log();
			return TriState.TRUE;
		}
		return TriState.DEFAULT;
	}

	private LiteralArgumentBuilder<ServerCommandSource> createCommand() {
		return literal("perms")
			.requires(Permissions.require(COMMAND_PERM, 2))
			.then(literal("reload")
				.executes(context -> {
					CompletableFuture.runAsync(this::loadPerms).thenRunAsync(() ->
						context.getSource().sendFeedback(
							() -> Text.of("Loaded " + allowedPerms.size() + " perms"),
							false
						)
					);
					return 0;
				})
			)
			.then(literal("list")
				.requires(Permissions.require(COMMAND_PERM + ".list", 2))
				.executes(context -> {
					context.getSource().sendFeedback(
						() -> Text.of(allowedPerms.stream().sorted().collect(Collectors.joining("\n"))),
						false
					);
					return 0;
				})
			)
			.then(literal("allow")
				.requires(Permissions.require(COMMAND_PERM + ".allow", 2))
				.then(argument("permission", StringArgumentType.string())
					.executes(context -> {
						String permission = StringArgumentType.getString(context, "permission");
						if (!permission.matches(REGEX_PERMISSION)) throw EXCEPTION_INVALID_PERM.create(permission);
						if (allowedPerms.add(permission))
							CompletableFuture.runAsync(this::writePerms);
						return 0;
					})
				)
			)
			.then(literal("revoke")
				.requires(Permissions.require(COMMAND_PERM + ".revoke", 2))
				.then(argument("permission", StringArgumentType.string())
					.suggests((context, builder) ->
						CommandSource.suggestMatching(allowedPerms.stream().sorted(), builder)
					)
					.executes(context -> {
						String permission = StringArgumentType.getString(context, "permission");
						if (allowedPerms.remove(permission))
							CompletableFuture.runAsync(this::writePerms);
						return 0;
					})
					.then(argument("recursive", BoolArgumentType.bool())
						.executes(context -> {
							String permission = StringArgumentType.getString(context, "permission");
							boolean recursive = BoolArgumentType.getBool(context, "recursive");
							boolean removed = recursive
								? allowedPerms.removeIf(p -> p.startsWith(permission))
								: allowedPerms.remove(permission);
							if (removed)
								CompletableFuture.runAsync(this::writePerms);
							return 0;
						})
					)
				)
			)
			.then(literal("clear")
				.requires(Permissions.require(COMMAND_PERM + ".clear", 2))
				.executes(context -> {
					if (!allowedPerms.isEmpty()) {
						allowedPerms.clear();
						CompletableFuture.runAsync(this::writePerms);
					}
					return 0;
				})
			);
	}
}
