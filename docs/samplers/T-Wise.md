# The t-wise sampler

This sampler returns a sample that achieves t-wise feature coverage, i.e., that covers all t-wise feature
interactions (a t-wise feature interaction is a partial configuration setting t features to on or off).

## Configuration

The t-wise sampler is configured using the following option:

- `t`
    - The parameter t for t-wise coverage.
    - Optional: no
- `max-samples`
    - The maximum number of samples to generate.
    - Optional: yes
    - Default: 2_147_483_647

For example, the sampler could be configured as follows:

```toml
[sampler]
name = "t-wise"
t = 2
```
