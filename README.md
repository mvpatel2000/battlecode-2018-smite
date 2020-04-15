# battlecode-2018-smite
Official Github repository of Team Smite for Battlecode 2018

Team Smite placed 2nd in the Sprint tournament, 3rd in the Seeding tournament, 4th in the HS tournament, and tied for 9th in the Final tournament.


# Sprint Strategy
Our sprint strategy was simple: Rangers and workers. After basic pathfinding was completed, ranger combat code was created and tuned early on in the week. The worker strategy was also simple. Replicate a minimum amount of times (the minimum number was based on the amount of karbonite), and then build factories whenever possible. If workers couldn't build a factory, they moved greedily towards the adjacent square with the most karbonite. Otherwise, they moved randomly. This simple strategy was powerful enough with the initial specifications to place 2nd, losing to JamesX2's Ranger-Healer combination.

# Seeding Strategy
Rangers were nerfed following the Sprint tournament, but not enough to render our strategy useless. We adopted the ranger-healer strategy, but forwent knights and mages, believing them useless. Our strategy, largely unchanged, but tweaked and tuned across the range of maps, worked well enough to place 3rd in the Seeding tournament, although clearly behind wcgw and Orbitary Graph. Orbitary stunned the tournement by relying on mages, which they realized could be used in conjunction with healers to create a powerful weapons. These "flying mages" worked as follows. First, mages would move to the ranger frontline. Then, they would "blink", or teleport towards the enemy, and perform their attack (which affected multiple squares at once, making them powerful weapons against factories). Then, the healers just behind the rangers would overcharge the mage. Overcharge was a special healer ability to reset the cooldown for any other unit. After a healer overcharged a mage, a mage could blink (teleport) and attack again. Then, another healer could overcharge the mage, and the process repeats as long as there are available healers. It is important to realize all of this occured on the same turn, devastating ranger frontlines and enemy factories.

# Qualifying Tournament Strategy
After the seeding tournament, the spec changes were drastic. Units cost double karbonite, and factories took three times longer to produce units. As a result, Mars became useless, since most games ended quickly. The beginning few workers and the first factory mattered greatly, especially on small or karbonite-sparse maps. We rarely reached critical ranger-healer mass. As a result, our team's strength dissipated and we began to tune the workers. More drastic changes were necessary to get the workers up to par, but the short turnaround time doomed us. In addition, development focus turned to knights, which proved a potent weapon in short-game close combat scenarios. We never got around to implementing or cloning Orbitary's flying mages. The spec changes and our weak workers doomed us to 9th place in the U.S. qualifying tournament. The top 12 moved on to the Final Tournament.

# Final Tournament
Submissions for the final tournament were due on Wednesday at 8 P.M., even though the tournament was taking place on Saturday evening. After the qualifying tournament the previous Sunday, the team's efforts largely shifted from Battlecode to the TJ Science Fair (occuring on Wednesday evening). Nevertheless, we found time to tune the workers slightly, and improve the knight combat slightly as well. We went into the final tournament knowing our bot was subpar compared to the rest of the top 16, especially on small rush maps and karbonite-sparse maps. Predictably, we finished tied for 9th place (9th-12th as a result of the double elimination tournanment format).

# Major Oversights
At the finalist dinner, we found out two critical elements that had doomed us. First, all our combat units moved, and then fired. However, because we never called the Unit after the move, the old instance of the object had a shorter combat range. In other words, we moved a step from the center of the combat range, but never updated the combat range. This especially affected our knights, since their combat range was only 2. As a result, they move in the direction of an enemy, but wait until the next turn to actually attack, and thus die rather quickly. The second oversight involved factory production. We never realized that one can simply call the enemy factory's garrison, and see what unit is being built. So, if the enemy was building a knight, we could have built a mage in response. However, since we didn't know this was possible, our early factory production suffered greatly, since we often produced the wrong types of unit, as we just guessed what the enemy was probably producing based on factory distance.

These two oversights affected our performance greatly since they both harmed our short-range combat and early game production. And, since the final tournament was mostly rush maps with little karbonite, early game performance hinged on these factors. 

# Final Thoughts
Next year, we'll look into the specs a bit more closely, and makes sure to exploit any (intended or unintended) API features. We've had fun writing our bots through the hectic January, and we appreciate Teh Devs flying us out to MIT for the final tournament. We had a blast at the finalist dinner, meeting other competitors as well as former classmates. We look forward to Battlecode 2019!

# Previous Years
* Mihir and Nikhil were part of Team Segault in [2017](https://github.com/nthistle/battlecode-2017-segfault)
