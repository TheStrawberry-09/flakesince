package com.thanawat.flakesince.testcase;

import com.thanawat.flakesince.project.Project;
import jakarta.persistence.*;

@Entity
@Table(name = "test_case")
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private String suite;

    @Column(nullable = false)
    private String name;

    protected TestCase() {
    }

    public TestCase(Project project, String suite, String name) {
        this.project = project;
        this.suite = suite;
        this.name = name;
    }

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public String getSuite() { return suite; }
    public String getName() { return name; }
}