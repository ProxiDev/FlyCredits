package me.ladyproxima.flycredits.commands;

import org.bukkit.command.CommandSender;

public interface ICommand {

    boolean executeCommand(CommandSender sender, String[] args);

    String requiredPermissions();

}
