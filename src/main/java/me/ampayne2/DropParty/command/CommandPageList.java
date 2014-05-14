/*
 * This file is part of DropParty.
 *
 * Copyright (c) 2013-2013 <http://dev.bukkit.org/server-mods/dropparty//>
 *
 * DropParty is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * DropParty is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with DropParty.  If not, see <http://www.gnu.org/licenses/>.
 */
package me.ampayne2.dropparty.command;

import me.ampayne2.dropparty.DropParty;
import me.ampayne2.dropparty.message.Messenger;
import me.ampayne2.dropparty.message.PageList;

import java.util.ArrayList;
import java.util.List;

/**
 * A PageList that lists all of the drop party commands and their description.
 */
public class CommandPageList extends PageList {
    public CommandPageList(DropParty dropParty, Command command) {
        super(dropParty, "Commands", 8);
        List<String> strings = new ArrayList<>();
        for (Command child : command.getChildren(true)) {
            strings.add(Messenger.PRIMARY_COLOR + ((DPCommand) child).getCommandUsage());
            strings.add(Messenger.SECONDARY_COLOR + "-" + ((DPCommand) child).getDescription());
        }
        setStrings(strings);
    }
}