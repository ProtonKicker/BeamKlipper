package ru.ytkab0bp.beamklipper

enum class InstanceIcon(@JvmField val drawable: Int) {
    CAR(R.drawable.ic_profile_car_28),
    HEXAGON(R.drawable.ic_profile_hexagon_28),
    FIGURINE(R.drawable.ic_profile_figurine_28),
    HOME(R.drawable.ic_profile_home_28),
    EYES(R.drawable.ic_profile_eyes_28),
    STAR(R.drawable.ic_profile_star_28),
    GEAR(R.drawable.ic_profile_gear_28),
    PRINTER(R.drawable.ic_printer_outline_28),
    ROBOT_ARM(R.drawable.ic_robot_arm_outline_28),
    LIGHTNING(R.drawable.ic_lightning_bolt_outline_28),
    FLAME(R.drawable.ic_flame_outline_28),
    VEHICLE(R.drawable.ic_vehicle_outline_28),
    HONEYCOMB(R.drawable.ic_honeycomb_outline_28),
    ROBOT_HEAD(R.drawable.ic_robot_head_outline_28),
    COMPASS(R.drawable.ic_drafting_compass_outline_28),
    DIVIDERS(R.drawable.ic_dividers_outline_28),
    GRID(R.drawable.ic_grid_layout_outline_28),
    BOX(R.drawable.ic_cube_box_outline_28),
    GAME(R.drawable.ic_game_outline_28),
    MAGIC_HAT(R.drawable.ic_magic_hat_outline_28),
    MOON(R.drawable.ic_moon_outline_28),
    INBOX(R.drawable.ic_inbox_outline_28),
    LOCATION(R.drawable.ic_location_outline_28),
    ROBOT(R.drawable.ic_robot_outline_28),
    SERVICES(R.drawable.ic_services_outline_28),
    SHOPPING_CART(R.drawable.ic_shopping_cart_outline_28),
    TRUCK(R.drawable.ic_truck_outline_28),
    SNEAKER(R.drawable.ic_sneaker_outline_28);

    companion object {
        @JvmStatic
        fun byKey(key: String): InstanceIcon {
            for (i in values()) {
                if (i.name == key) return i
            }
            return CAR
        }
    }
}
