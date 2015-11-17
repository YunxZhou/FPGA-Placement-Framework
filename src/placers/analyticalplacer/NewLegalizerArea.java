package placers.analyticalplacer;

import java.util.ArrayList;
import java.util.List;

import circuit.architecture.BlockType;

class NewLegalizerArea {

    int top, bottom, left, right;

    private int blockHeight, blockRepeat;

    private double tileCapacity;

    private int numTiles = 0;
    private List<Integer> blockIndexes = new ArrayList<Integer>();


    private int[][] growDirections = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
    private boolean[] originalDirection = {true, true, true, true};
    private int growDirectionIndex = -1;

    NewLegalizerArea(NewLegalizerArea a) {
        this.top = a.top;
        this.bottom = a.bottom;
        this.left = a.left;
        this.right = a.right;

        this.tileCapacity = a.tileCapacity;
        this.blockHeight = a.blockHeight;
        this.blockRepeat = a.blockRepeat;
    }

    NewLegalizerArea(int x, int y, double tileCapacity, BlockType blockType) {
        this.left = x;
        this.right = x;
        this.top = y;
        this.bottom = y;

        this.tileCapacity = tileCapacity;
        this.blockHeight = blockType.getHeight();
        this.blockRepeat = blockType.getRepeat();
    }


    int getBlockHeight() {
        return this.blockHeight;
    }
    int getBlockRepeat() {
        return this.blockRepeat;
    }


    void grow(int[] direction) {
        this.grow(direction[0], direction[1]);
    }
    void grow(int horizontal, int vertical) {
        if(horizontal == -1) {
            this.left -= this.blockRepeat;

        } else if(horizontal == 1) {
            this.right += this.blockRepeat;

        } else if(vertical == -1) {
            this.top -= this.blockHeight;

        } else if(vertical == 1) {
            this.bottom += this.blockHeight;
        }
    }


    void incrementTiles() {
        this.numTiles++;
    }
    double getCapacity() {
        return this.numTiles * this.tileCapacity;
    }


    void addBlockIndexes(List<Integer> blockIndexes) {
        this.blockIndexes.addAll(blockIndexes);
    }
    List<Integer> getBlockIndexes() {
        return this.blockIndexes;
    }
    int getOccupation() {
        return this.blockIndexes.size();
    }


    int[] nextGrowDirection() {
        int[] direction;
        do {
            this.growDirectionIndex = (this.growDirectionIndex + 1) % 4;
            direction = this.growDirections[this.growDirectionIndex];
        } while(direction[0] == 0 && direction[1] == 0);

        return direction;
    }

    void grow() {
        int[] direction = this.growDirections[this.growDirectionIndex];
        if(direction[1] == 0) {
            if(direction[0] == 1) {
                this.right += this.blockRepeat;

            } else {
                this.left -= this.blockRepeat;
            }

        } else {
            if(direction[1] == 1) {
                this.bottom += this.blockHeight;
            } else {
                this.top -= this.blockHeight;
            }
        }
    }

    void disableDirection() {
        int index = this.growDirectionIndex;
        int oppositeIndex = (index + 2) % 4;

        if(this.originalDirection[index]) {
            if(!this.originalDirection[oppositeIndex]) {
                this.growDirections[oppositeIndex][0] = 0;
                this.growDirections[oppositeIndex][1] = 0;
            }

            this.originalDirection[index] = false;
            this.growDirections[index][0] = this.growDirections[oppositeIndex][0];
            this.growDirections[index][1] = this.growDirections[oppositeIndex][1];

        } else {
            this.growDirections[index][0] = 0;
            this.growDirections[index][1] = 0;
            this.growDirections[oppositeIndex][0] = 0;
            this.growDirections[oppositeIndex][1] = 0;
        }

        // Make sure the replacement for the current grow direction is chosen next,
        // since growing in the current direction must have failede
        this.growDirectionIndex--;
    }


    @Override
    public String toString() {
        return String.format("[[%d, %d], [%d, %d]", this.left, this.top, this.right, this.bottom);
    }
}
