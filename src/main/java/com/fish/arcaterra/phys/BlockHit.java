package com.fish.arcaterra.phys;

public class BlockHit {
    public final int x, y, z;
    public final int nx, ny, nz; // 法线方向

    public BlockHit(int x, int y, int z, int nx, int ny, int nz) {
        this.x = x; this.y = y; this.z = z;
        this.nx = nx; this.ny = ny; this.nz = nz;
    }
}