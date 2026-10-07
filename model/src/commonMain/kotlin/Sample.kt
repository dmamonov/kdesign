package kdesign

fun sampleProject(): Project {
    fun tile(id: String, name: String, rows: List<String>, colors: Map<Char, Int>) = Tile(id, name, rows.size, rows.flatMap { row -> row.map { colors[it] ?: -1 } })
    val mushroom = tile("tile-mushroom", "Ember mushroom", listOf("................","......rrrr......","....rrrrrrrr....","...rrwwrrwwrr...","..rrrwwrrwwrrr..","..rrrrrrrrrrrr..",".rrrrrrwwrrrrrr.",".rrrrrrwwrrrrrr.",".dddddddddddddd.",".....ssssss.....",".....swwwws.....",".....swwwws.....",".....swwwws.....","....sswwwwss....","....ssssssss....","................"), mapOf('r' to 22, 'w' to 55, 'd' to 6, 's' to 39))
    val grass = tile("tile-grass", "Mossy ground", List(16) { y -> (0..15).map { x -> when { y < 2 -> if ((x+y)%4==0) 'l' else 'g'; y < 5 -> if ((x*3+y)%5<2) 'g' else 'd'; (x*7+y*3)%17<3 -> 's'; else -> 'd' } }.joinToString("") }, mapOf('g' to 25, 'l' to 41, 'd' to 7, 's' to 23))
    val stone = tile("tile-stone", "Ancient stone", List(16) { y -> (0..15).map { x -> if(y%8==0 || (x+(y/8)*8)%16==0) 'd' else if(y%8==1) 'l' else 's' }.joinToString("") }, mapOf('d' to 13, 'l' to 16, 's' to 45))
    val door = tile("tile-door", "Cave threshold", listOf(".....ssssss.....","...sssssssss....","..sssddddssss...",".sssddddddssss..",".ssddddddddsss..","sssddddddddssss.","sssddddddddssss.","sssddddddddssss.","sssddddddddssss.","sssddddddddssss.","sssddddddddssss.","sssddddddddssss.","sssddddddddssss.","sssddddddddssss.","sssddddddddssss.","sssssssssssssss."), mapOf('s' to 45, 'd' to 13))
    val tiles = listOf(mushroom, grass, stone, door)
    val blocks = tiles.map { Block("block-${it.id.removePrefix("tile-")}", it.name, width = 16, height = 16, children = listOf(Placement("tile", it.id, 0, 0)), conceptIds = if(it.id=="tile-mushroom") listOf("concept-quest") else emptyList(), properties = mapOf("semantic" to if(it.id=="tile-mushroom") "collectible" else "terrain", "solid" to (it.id!="tile-mushroom").toString())) }.toMutableList()
    blocks += Block("meta-entrance", "Cave entrance", "meta", 16, 16, listOf(Placement("arch", "block-door", 0, 0)), conceptIds = listOf("concept-gate"), portals = listOf(Portal("Enter the hollow", "level-cave")))
    blocks += Block("meta-clearing", "The mosswood clearing", "meta", 512, 192, buildList {
        repeat(32) { add(Placement("ground-$it", "block-grass", it*16, 160)); add(Placement("earth-$it", "block-stone", it*16, 176)) }
        listOf(5,6,7,8,14,15,16,22,23,24,25).forEach { add(Placement("platform-$it", "block-grass", it*16, if(it<10) 112 else if(it<20) 80 else 112)) }
        listOf(3,7,15,21,28).forEachIndexed { i,x -> add(Placement("mushroom-$i", "block-mushroom", x*16, when(x) { 7 -> 96; 15 -> 64; else -> 144 })) }
        add(Placement("cave-door", "meta-entrance", 448, 144))
    })
    blocks += Block("meta-cave", "The lantern hollow", "meta", 320, 160, (0..19).map { Placement("floor-$it", "block-stone", it*16, 144) } + Placement("hidden-mushroom", "block-mushroom", 160, 128), portals = listOf(Portal("Return to mosswood", "level-woods", listOf("cave-door"))))
    return Project(tiles = tiles, blocks = blocks, levels = listOf(
        Level("level-woods", "Mosswood approach", "meta-clearing", notes = mapOf("description" to "A quiet forest at the edge of something forgotten. Follow the ember mushrooms into the hollow.", "playerMechanics" to "Run, jump, collect, interact", "playerGoals" to "Find the cave entrance; collect five ember mushrooms", "designGoals" to "Teach vertical exploration and establish a return path", "emotionalBaits" to "Curiosity · shelter · a light in the dark", "characters" to "The wandering botanist", "enemies" to "Moss crawlers", "objects" to "Ember mushrooms, sealed cave door")),
        Level("level-cave", "The lantern hollow", "meta-cave", "level-woods", mapOf("description" to "A small refuge below the forest. The return route leads back to the clearing.", "playerMechanics" to "Jump, interact", "playerGoals" to "Discover the hidden mushroom", "designGoals" to "Reward exploration", "emotionalBaits" to "Relief, discovery", "characters" to "Lantern keeper", "enemies" to "None", "objects" to "Ember mushroom"))
    ), concepts = listOf(Concept("concept-gate", "The overgrown threshold", "Gates", "Inventory object", "Bring five ember mushrooms to the botanist to open the hollow."), Concept("concept-quest", "A light for the keeper", "Quests", "Collect", "Collect five ember mushrooms throughout Mosswood."), Concept("concept-jump", "A small leap", "Mechanics", "Movement", "Reach higher paths with a short, forgiving jump.")))
}
