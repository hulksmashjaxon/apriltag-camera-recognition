package org.example;

public class Enum {
    public enum Ids {
        RotateToApriltag(12),
        NoSuchAction(-1);

        private final int id;

        Ids(int i) {
            this.id = i;
        }

        public int getId() {
            return id;
        }

        public static Ids fromId(int id) {
            for (Ids type : values()) {
                if (type.getId() == id) {
                    return type;
                }
            }
            return Ids.NoSuchAction;
        }
    };
}
