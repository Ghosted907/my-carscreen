package com.ghosted907.t1launcher

/** Never target display 0, the launcher, the stock cluster, or Layl Najd's package. */
object ClusterTaskPolicy {
    fun removable(display: Int, cluster: Int, pkg: String?, tracked: Set<String>, launcher: String): Boolean =
        display == cluster && cluster == 1 && pkg != null && pkg in tracked && pkg != launcher && pkg != "com.ghosted907.clusterdiag"
}
