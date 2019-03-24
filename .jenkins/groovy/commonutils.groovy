/**
 * Extracts version defined in the Helm chart file located at provided path
 *
 * Params:
 *   - helmChartFile : Path relative from current working directory to Helm chart
 *   - releaseBranch : Used to determine how to create version. If on this branch,
 *                     version is just the semantic version in Helm chart. Otherwise,
 *                     branch name is appended.
 */
String getVersionFromHelmChart(String helmChartFile, String releaseBranch) {

    String version = null

    // Read Helm Chart file line by line
    readFile(helmChartFile).split('\r|\n').each({ line ->

        // Look for line that starts with 'appVersion'
        if(line.trim().startsWith("appVersion")) {

            // Strip out everything on the line except the semantic version (i.e. #.#.#)
            version = line.replaceFirst(".*appVersion.*(\\d+\\.\\d+\\.\\d+).*", "\$1")

            // If not on release branch, append branch name to semantic version
            if(! releaseBranch.equals(BRANCH_NAME)) {
                version = version + '-' + BRANCH_NAME
                // feature branches may have the 'feature/branch-name' structure
                // replace any '/' with '-' to keep version useable as image tag
                version = version.replace('/', '-')
            }
        }
    })

    if (version) {
        return version
    } else {
        throw new RuntimeException('Unable to determine version from Helm chart "' + helmChartFile + '"')
    }

}

/**
 * Creates a Git tag on current commit using provided release version. Also,
 * increments the patch component of the Helm chart version and creates new
 * commit on provided branch
 *
 * Params:
 *   - gitUser            : Git repository user (will be used to push tag and commit)
 *   - gitPassword        : Git repository password
 *   - branchToIncrement  : Main branch of repo whose commits trigger builds
 *   - releaseVersion     : Current release version being built and deployed
 *   - helmChartFile      : Path relative from current working directory to Helm chart
 */
def tagCommitAndIncrementVersion(String gitUser, String gitPassword, String branchToIncrement, String releaseVersion, String helmChartFile) {

    sh '''

    # Configure Git for tagging/committing and pushing
    ORIGIN=$(echo "$(git config remote.origin.url)" | sed -E "s~(http[s]*://)~\\1''' + gitUser + '''@~")
    git config --global user.email "jenkins@email.com"
    git config --global user.name "Jenkins"
    printf "exec echo \\"''' + gitPassword + '''\\"" > "$PWD/askgitpass.sh"
    chmod a+x "$PWD/askgitpass.sh"

    # Tag Release Candidate
    TAG="v''' + releaseVersion + '''"
    git tag -a "$TAG" -m "Release $TAG created and delivered"
    GIT_ASKPASS="$PWD/askgitpass.sh" git push "$ORIGIN" "$TAG"

    # Increment version on main branch
    git checkout ''' + branchToIncrement + '''
    git reset --hard origin/''' + branchToIncrement + '''
    new_version="$(echo "''' + releaseVersion + '''" | cut -d '.' -f 1,2).$(($(echo "''' + releaseVersion + '''" | cut -d '.' -f 3) + 1))"
    sed -i -E s/"appVersion.*[0-9]+\\.[0-9]+\\.[0-9]+"/"appVersion: $new_version"/ ''' + helmChartFile +'''
    git commit -a -m "Updated app version from ''' + releaseVersion + ''' to $new_version"
    GIT_ASKPASS="$PWD/askgitpass.sh" git push "$ORIGIN" ''' + branchToIncrement

}

return this