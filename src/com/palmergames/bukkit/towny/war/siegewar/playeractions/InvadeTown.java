package com.palmergames.bukkit.towny.war.siegewar.playeractions;

import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.TownyMessaging;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.exceptions.AlreadyRegisteredException;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.Coord;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.Translation;
import com.palmergames.bukkit.towny.war.siegewar.enums.SiegeStatus;
import com.palmergames.bukkit.towny.war.siegewar.objects.Siege;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * This class is responsible for processing requests to invade towns
 *
 * @author Goosius
 */
public class InvadeTown {

	/**
	 * Process an invade town request
	 *
	 * This method does some final checks and if they pass, the invasion is executed.
	 *
	 * @param plugin the town plugin object
	 * @param player the player who placed the invade banner
	 * @param townToBeInvaded the town to be invaded
	 * @param event the place block event
	 */
    public static void processInvadeTownRequest(Towny plugin,
                                                Town townOfInvadingResident,
                                                Town townToBeInvaded,
                                                BlockPlaceEvent event) {
        try {

			Siege siege = townToBeInvaded.getSiege();
			if (siege.getStatus() != SiegeStatus.ATTACKER_WIN && siege.getStatus() != SiegeStatus.DEFENDER_SURRENDER)
				throw new TownyException(Translation.of("msg_err_siege_war_cannot_invade_without_victory"));

			Nation nationOfInvadingResident = townOfInvadingResident.getNation();
			Nation attackerWinner = siege.getAttackingNation();
			
			if (nationOfInvadingResident != attackerWinner)
				throw new TownyException(Translation.of("msg_err_siege_war_cannot_invade_without_victory"));

            if (siege.isTownInvaded())
                throw new TownyException(String.format(Translation.of("msg_err_siege_war_town_already_invaded"), townToBeInvaded.getName()));

			if(townToBeInvaded.hasNation() && townToBeInvaded.getNation() == attackerWinner)
				throw new TownyException(String.format(Translation.of("msg_err_siege_war_town_already_belongs_to_your_nation"), townToBeInvaded.getName()));

			if (TownySettings.getNationRequiresProximity() > 0) {
				Coord capitalCoord = attackerWinner.getCapital().getHomeBlock().getCoord();
				Coord townCoord = townToBeInvaded.getHomeBlock().getCoord();
				if (!attackerWinner.getCapital().getHomeBlock().getWorld().getName().equals(townToBeInvaded.getHomeBlock().getWorld().getName())) {
					throw new TownyException(Translation.of("msg_err_nation_homeblock_in_another_world"));
				}
				double distance;
				distance = Math.sqrt(Math.pow(capitalCoord.getX() - townCoord.getX(), 2) + Math.pow(capitalCoord.getZ() - townCoord.getZ(), 2));
				if (distance > TownySettings.getNationRequiresProximity()) {
					throw new TownyException(String.format(Translation.of("msg_err_town_not_close_enough_to_nation"), townToBeInvaded.getName()));
				}
			}

			if (TownySettings.getMaxTownsPerNation() > 0) {
				if (attackerWinner.getTowns().size() >= TownySettings.getMaxTownsPerNation()){
					throw new TownyException(String.format(Translation.of("msg_err_nation_over_town_limit"), TownySettings.getMaxTownsPerNation()));
				}
			}

			captureTown(plugin, siege, attackerWinner, townToBeInvaded);

        } catch (TownyException x) {
			event.setBuild(false);
			event.setCancelled(true);
//            TownyMessaging.sendErrorMsg(player, x.getMessage());
        } catch (Exception e) {
			event.setBuild(false);
			event.setCancelled(true);
			TownyMessaging.sendErrorMsg("Problem invading town. Contact server support team.");
			System.out.println("Unexpected problem invading town");
			e.printStackTrace();
		}
    }

    private static void captureTown(Towny plugin, Siege siege, Nation attackingNation, Town defendingTown) {
		Nation nationOfDefendingTown = null;
		boolean nationTown = false;
		boolean nationDefeated = false;
		
        if(defendingTown.hasNation()) {
			nationTown = true;
			
            try {
                nationOfDefendingTown = defendingTown.getNation();
            } catch (NotRegisteredException x) {}

            // This will delete the Nation when it loses its last town, mark them defeated.
            if(nationOfDefendingTown.getTowns().size() == 1) {
               	nationDefeated = true;
            }
            
			//Remove town from nation (and nation itself if empty)
            defendingTown.removeNation();
        }
        
        // Add town to nation.
        try {
			defendingTown.setNation(attackingNation);
		} catch (AlreadyRegisteredException ignored) {}
        
        //Set flags to indicate success
		siege.setTownInvaded(true);
        defendingTown.setOccupied(true);

		//Save to db
		TownyUniverse.getInstance().getDataSource().saveSiege(siege);
		TownyUniverse.getInstance().getDataSource().saveTown(defendingTown);
		TownyUniverse.getInstance().getDataSource().saveNation(attackingNation);
		if(nationTown && !nationDefeated) {
			TownyUniverse.getInstance().getDataSource().saveNation(nationOfDefendingTown);
		}
		
		//Messaging
		if(nationTown) {
			TownyMessaging.sendGlobalMessage(String.format(
				Translation.of("msg_siege_war_nation_town_captured"),
				defendingTown.getFormattedName(),
				nationOfDefendingTown.getFormattedName(),
				attackingNation.getFormattedName()
			));
		} else {
			TownyMessaging.sendGlobalMessage(String.format(
				Translation.of("msg_siege_war_neutral_town_captured"),
				defendingTown.getFormattedName(),
				attackingNation.getFormattedName()
			));
		}
		if(nationDefeated) {
			TownyMessaging.sendGlobalMessage(String.format(
				Translation.of("msg_siege_war_nation_defeated"),
				nationOfDefendingTown.getFormattedName()
			));
		}
    }
}
