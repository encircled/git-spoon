# Git Spoon
Gitops CD for Kubernetes.

The main goal of Git spoon is synchronization the state in git repositories into your cluster. Each git repository contains kubernetes manifests of a particular application.

Git spoon is running inside your cluster.

## Configuration

Configuration of deployments is stored in git as well, this repository must have configuration file `configuration.json`, for example: 

```json
{
    "version": "1.0",
    "deployments": {
        "app1": {
            "repository": "git@git.my.net:path/to/repository.git",
            "interval": "2M",
            "namespace": "mega-app"
        },
        "my-app2": {
            "repository": "git@git.my.net:path/to/another/repository.git",
            "interval": "30S",
            "namespace": "my-app"
         }
    }
}
```
where:
- `repository` - path to repository with kubernetes manifest files
- `interval` - changes polling interval represented in duration units (i.e. 2M is 2 minutes and 30S is 30 seconds)
- `namespace` - target kubernetes namespace

Changes in deployment configuration are synchronized each few minutes in runtime, without need to restart Git spoon

Path to configuration repository must be passed either via args (`--configRepo`) or via environment varialbe (`configRepo`)

